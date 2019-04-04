(ns rems.test-form-validation
  (:require [clojure.test :refer :all]
            [rems.form-validation :refer [validate]]))

(deftest test-validate
  (testing "all fields filled & all licenses accepted"
    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional false
                             :value "a"}]
                    :licenses [{:title "LGPL"
                                :approved true}]}))))

  (testing "optional fields"
    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional true
                             :value nil}
                            {:title "B"
                             :optional true
                             :value ""}]}))))

  (testing "error: license required"
    (is (= [{:type :t.form.validation/required :license-id 123}]
           (validate
            {:items [{:title "A"
                      :optional false
                      :value "a"}]
             :licenses [{:id 123
                         :title "LGPL"}]}))))

  (testing "error: field required"
    (is (= [{:type :t.form.validation/required :field-id 2}
            {:type :t.form.validation/required :field-id 3}]
           (validate
            {:items [{:id 1
                      :localizations {:en {:title "A"}}
                      :optional true
                      :value nil}
                     {:id 2
                      :localizations {:en {:title "B"}}
                      :optional false
                      :value ""}
                     {:id 3
                      :localizations {:en {:title "C"}}
                      :optional false
                      :value nil}]}))))

  (testing "error: field input too long"
    (is (= [{:type :t.form.validation/toolong :field-id 2}]
           (validate
            {:items [{:id 1
                      :localizations {:en {:title "A"}}
                      :maxlength 5
                      :value "abcde"}
                     {:id 2
                      :localizations {:en {:title "B"}}
                      :maxlength 5
                      :value "abcdef"}]})))))