(ns ^:integration rems.api.test-forms
  (:require [clojure.test :refer :all]
            [rems.api.schema :as schema]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(defn- fixup-field-visible-type [field]
  (if (get-in field [:field/visibility :visibility/type])
    (update-in field [:field/visibility :visibility/type] keyword)
    field))

(defn fixup-field-id-to-generated [field] ; TODO: remove when generation is removed from backend
  (if (get-in field [:field/visibility :visibility/field :field/id])
    (update-in field [:field/visibility :visibility/field :field/id] dec)
    field))

(defn fixup-field-to-match-command [field]
  (-> field
      (dissoc :field/id)
      ;; XXX: these tests use the JSON API, so keywords are not maintained
      (update :field/type keyword)
      fixup-field-visible-type
      fixup-field-id-to-generated))

(deftest forms-api-test
  (let [api-key "42"
        user-id "owner"]

    (testing "get all"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:form/id (first data)))))

    (testing "get one"
      (let [data (-> (request :get "/api/forms/1")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:form/id data))))

    (testing "not found"
      (let [response (-> (request :get "/api/forms/0")
                         (authenticate api-key user-id)
                         handler)]
        (is (= 404 (:status response)))))

    (testing "create"
      (let [command {:form/organization "abc"
                     :form/title (str "form title " (UUID/randomUUID))
                     :form/fields [{:field/title {:en "en title"
                                                  :fi "fi title"}
                                    :field/optional true
                                    :field/type :text
                                    :field/placeholder {:en "en placeholder"
                                                        :fi "fi placeholder"}}]}]

        (testing "invalid create"
          ;; TODO: silence the logging for this expected error
          (let [command-with-invalid-max-length (assoc-in command [:form/fields 0 :field/max-length] -1)
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-invalid-max-length)
                             handler)]
            (is (= 400 (:status response))
                "can't send negative max length")))

        (testing "valid create"
          (let [id (-> (request :post "/api/forms/create")
                       (authenticate api-key user-id)
                       (json-body command)
                       handler
                       read-ok-body
                       :id)]
            (is id)
            (testing "and fetch"
              (let [form-template (-> (request :get (str "/api/forms/" id))
                                      (authenticate api-key user-id)
                                      handler
                                      read-ok-body)]
                (testing "result matches input"
                  (is (= (select-keys command [:form/organization :form/title])
                         (select-keys form-template [:form/organization :form/title])))
                  (is (= (:form/fields command)
                         (mapv fixup-field-to-match-command (:form/fields form-template)))))))))))))

(deftest forms-api-all-field-types-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi"}
        form-spec {:form/organization "abc"
                   :form/title "all field types test"
                   :form/fields [{:field/type :text
                                  :field/title localized
                                  :field/optional false}
                                 {:field/type :texta
                                  :field/title localized
                                  :field/optional true
                                  :field/max-length 300
                                  :field/placeholder localized}
                                 {:field/type :description
                                  :field/title localized
                                  :field/optional false}
                                 {:field/type :option
                                  :field/title localized
                                  :field/optional true
                                  :field/options [{:key "a" :label localized}
                                                  {:key "b" :label localized}
                                                  {:key "c" :label localized}]}
                                 {:field/type :multiselect
                                  :field/title localized
                                  :field/optional false
                                  :field/options [{:key "a" :label localized}
                                                  {:key "b" :label localized}
                                                  {:key "c" :label localized}
                                                  {:key "d" :label localized}]}
                                 {:field/type :label
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :header
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :email
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :date
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :attachment
                                  :field/title localized
                                  :field/optional false}]}]
    (is (= (:vs (:field/type schema/FieldTemplate))
           (set (map :field/type (:form/fields form-spec))))
        "a new field has been added to schema but not to this test")

    (testing "creating"
      (let [form-id (-> (request :post "/api/forms/create")
                        (authenticate api-key user-id)
                        (json-body form-spec)
                        handler
                        read-ok-body
                        :id)]
        (is form-id)
        (testing "and fetching"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (select-keys form-spec [:form/organization :form/title])
                   (select-keys form [:form/organization :form/title])))
            (is (= (:form/fields form-spec)
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest form-editable-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:form/organization "abc"
                                :form/title "form editable test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "New form is editable"
      (is (:success (-> (request :get (str "/api/forms/" form-id "/editable"))
                        (authenticate api-key user-id)
                        handler
                        read-ok-body))))
    (let [data (-> (request :post "/api/catalogue-items/create")
                   (authenticate api-key user-id)
                   (json-body {:form form-id
                               :resid 1
                               :wfid 1
                               :archived false
                               :localizations {}})
                   handler
                   read-body)]
      (testing "Form is non-editable after in use by a catalogue item"
        (is (not (:success (-> (request :get (str "/api/forms/" form-id "/editable"))
                               (authenticate api-key user-id)
                               handler
                               read-ok-body))))))))

(deftest form-edit-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:form/organization "abc"
                                :form/title "form edit test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "form content before editing"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (= (:form/organization form) "abc")))
      (let [response (-> (request :put "/api/forms/edit")
                         (authenticate api-key user-id)
                         (json-body {:form/id form-id
                                     :form/organization "def"
                                     :form/title "form edit test"
                                     :form/fields []})
                         handler
                         read-ok-body)]
        (testing "form content after editing"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (:form/organization form) "def"))))))))

(deftest form-enabled-archived-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:form/organization "abc"
                                :form/title "form update test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (is (not (nil? form-id)))
    (testing "disable"
      (is (:success (-> (request :put "/api/forms/enabled")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled false})
                        handler
                        read-ok-body))))
    (testing "archive"
      (is (:success (-> (request :put "/api/forms/archived")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :archived true})
                        handler
                        read-ok-body))))
    (testing "fetch"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (false? (:enabled form)))
        (is (true? (:archived form)))))
    (testing "unarchive"
      (is (:success (-> (request :put "/api/forms/archived")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :archived false})
                        handler
                        read-ok-body))))
    (testing "enable"
      (is (:success (-> (request :put "/api/forms/enabled")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled true})
                        handler
                        read-ok-body))))
    (testing "fetch again"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (true? (:enabled form)))
        (is (false? (:archived form)))))))

(deftest option-form-item-test
  (let [api-key "42"
        user-id "owner"]
    (testing "create"
      (let [command {:form/organization "abc"
                     :form/title (str "form title " (UUID/randomUUID))
                     :form/fields [{:field/title {:en "en title"
                                                  :fi "fi title"}
                                    :field/optional true
                                    :field/type :option
                                    :field/options [{:key "yes"
                                                     :label {:en "Yes"
                                                             :fi "Kyllä"}}
                                                    {:key "no"
                                                     :label {:en "No"
                                                             :fi "Ei"}}]}]}
            id (-> (request :post "/api/forms/create")
                   (authenticate api-key user-id)
                   (json-body command)
                   handler
                   read-ok-body
                   :id)]

        (testing "and fetch"
          (let [form (-> (request :get (str "/api/forms/" id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (:form/fields command)
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest forms-api-visible-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi"}
        command {:form/organization "abc"
                 :form/title "text fields that depend on a field"
                 :form/fields [{:field/type :option
                                :field/title localized
                                :field/optional false
                                :field/options [{:key "a" :label localized}
                                                {:key "b" :label localized}
                                                {:key "c" :label localized}]}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :always}}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :only-if
                                                   :visibility/field {:field/id 0}
                                                   :visibility/value ["c"]}}
                               {:field/type :multiselect
                                :field/title localized
                                :field/optional false
                                :field/options [{:key "a" :label localized}
                                                {:key "b" :label localized}
                                                {:key "c" :label localized}
                                                {:key "d" :label localized}]}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :only-if
                                                   :visibility/field {:field/id 3}
                                                   :visibility/value ["c" "d"]}}]}]
    (testing "creating"
      (let [form-id (-> (request :post "/api/forms/create")
                        (authenticate api-key user-id)
                        (json-body command)
                        handler
                        read-ok-body
                        :id)]
        (is form-id)
        (testing "and fetching"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (select-keys command [:form/organization :form/title])
                   (select-keys form [:form/organization :form/title])))
            (is (= (update-in (:form/fields command) [1] dissoc :field/visibility) ; always visible field is not saved as it's the default
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest forms-api-filtering-test
  (let [unfiltered (-> (request :get "/api/forms" {:archived true})
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/forms")
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :archived) unfiltered))
    (is (not-any? :archived filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest forms-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         handler)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (json-body {:form/organization "abc"
                                     :form/title "the title"
                                     :form/fields []})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         (authenticate "42" "alice")
                         handler)
            body (read-body response)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (authenticate "42" "alice")
                         (json-body {:form/organization "abc"
                                     :form/title "the title"
                                     :form/fields []})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
