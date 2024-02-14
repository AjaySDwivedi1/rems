(ns rems.service.test-data
  "Generate and populate database with usable test data. Contains multiple high-level
   test data helper functions. Separate functions are provided to generate complete
   test and demo data, which create the same data, but with different users."
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [rems.api.schema :as schema]
            [rems.config]
            [rems.context :as context]
            [rems.db.api-key]
            [rems.db.core :as db]
            [rems.db.roles]
            [rems.db.test-data-helpers :as test-helpers :refer [getx-test-users getx-test-user-data]]
            [rems.db.test-data-users :refer [+bot-user-data+ +bot-users+ +demo-user-data+ +demo-users+ +fake-user-data+ +fake-users+ +oidc-user-data+ +oidc-users+]]
            [rems.db.users]
            [rems.service.form]
            [rems.service.organizations]
            [rems.testing-util :refer [with-user]])
  (:import [java.util UUID]
           [java.util.concurrent Executors Future]))

(def label-field
  {:field/type :label
   :field/optional false
   :field/title {:en "This form demonstrates all possible field types. This is a link https://www.example.org/label (This text itself is a label field.)"
                 :fi "Tämä lomake havainnollistaa kaikkia mahdollisia kenttätyyppejä. Tämä on linkki https://www.example.org/label (Tämä teksti itsessään on lisätietokenttä.)"
                 :sv "Detta blanket visar alla möjliga fälttyper. Det här är en länk  https://www.example.org/label (Det här texten är en fält för tilläggsinformation.)"}})
(def description-field
  {:field/type :description
   :field/optional false
   :field/title {:en "Application title field"
                 :fi "Hakemuksen otsikko -kenttä"
                 :sv "Ansökningens rubrikfält"}})
(def text-field
  {:field/type :text
   :field/optional false
   :field/title {:en "Text field"
                 :fi "Tekstikenttä"
                 :sv "Textfält"}
   :field/info-text {:en "Explanation of how to fill in text field"
                     :fi "Selitys tekstikentän täyttämisestä"
                     :sv "Förklaring till hur man fyller i textfält"}
   :field/placeholder {:en "Placeholder text"
                       :fi "Täyteteksti"
                       :sv "Textexempel"}})
(def texta-field
  {:field/type :texta
   :field/optional false
   :field/title {:en "Text area"
                 :fi "Tekstialue"
                 :sv "Textområde"}
   :field/info-text {:en "Explanation of how to fill in text field"
                     :fi "Selitys tekstikentän täyttämisestä"
                     :sv "Förklaring till hur man fyller i textfält"}
   :field/placeholder {:en "Placeholder text"
                       :fi "Täyteteksti"
                       :sv "Textexempel"}})
(def header-field
  {:field/type :header
   :field/optional false
   :field/title {:en "Header"
                 :fi "Otsikko"
                 :sv "Titel"}})
(def date-field
  {:field/type :date
   :field/optional false
   :field/title {:en "Date field"
                 :fi "Päivämääräkenttä"
                 :sv "Datumfält"}})
(def email-field
  {:field/type :email
   :field/optional false
   :field/title {:en "Email field"
                 :fi "Sähköpostikenttä"
                 :sv "E-postaddressfält"}})
(def attachment-field
  {:field/type :attachment
   :field/optional false
   :field/title {:en "Attachment"
                 :fi "Liitetiedosto"
                 :sv "Bilaga"}})
(def multiselect-field
  {:field/type :multiselect
   :field/optional false
   :field/title {:en "Multi-select list"
                 :fi "Monivalintalista"
                 :sv "Lista med flerval"}
   :field/options [{:key "Option1"
                    :label {:en "First option"
                            :fi "Ensimmäinen vaihtoehto"
                            :sv "Första alternativ"}}
                   {:key "Option2"
                    :label {:en "Second option"
                            :fi "Toinen vaihtoehto"
                            :sv "Andra alternativ"}}
                   {:key "Option3"
                    :label {:en "Third option"
                            :fi "Kolmas vaihtoehto"
                            :sv "Tredje alternativ"}}]})
(def table-field
  {:field/type :table
   :field/optional false
   :field/title {:en "Table"
                 :fi "Taulukko"
                 :sv "Tabell"}
   :field/columns [{:key "col1"
                    :label {:en "First"
                            :fi "Ensimmäinen"
                            :sv "Första"}}
                   {:key "col2"
                    :label {:en "Second"
                            :fi "Toinen"
                            :sv "Andra"}}]})
(def option-field
  {:field/type :option
   :field/optional false
   :field/title {:en "Option list"
                 :fi "Valintalista"
                 :sv "Lista"}
   :field/options [{:key "Option1"
                    :label {:en "First option"
                            :fi "Ensimmäinen vaihtoehto"
                            :sv "Första alternativ"}}
                   {:key "Option2"
                    :label {:en "Second option"
                            :fi "Toinen vaihtoehto"
                            :sv "Andra alternativ"}}
                   {:key "Option3"
                    :label {:en "Third option"
                            :fi "Kolmas vaihtoehto"
                            :sv "Tredje alternativ"}}]})
(def phone-number-field
  {:field/type :phone-number
   :field/optional false
   :field/title {:en "Phone number"
                 :fi "Puhelinnumero"
                 :sv "Telefonnummer"}})
(def ip-address-field
  {:field/type :ip-address
   :field/optional false
   :field/title {:en "IP address"
                 :fi "IP-osoite"
                 :sv "IP-adress"}})

(def conditional-field-example
  [(merge option-field {:field/id "option"
                        :field/title {:en "Option list. Choose the first option to reveal a new field."
                                      :fi "Valintalista. Valitse ensimmäinen vaihtoehto paljastaaksesi uuden kentän."
                                      :sv "Lista. Välj det första alternativet för att visa ett nytt fält."}
                        :field/optional true})
   (merge text-field {:field/title {:en "Conditional field. Shown only if first option is selected above."
                                    :fi "Ehdollinen kenttä. Näytetään vain jos yllä valitaan ensimmäinen vaihtoehto."
                                    :sv "Villkorlig fält. Visas bara som första alternativet har väljats ovan."}
                      :field/visibility {:visibility/type :only-if
                                         :visibility/field {:field/id "option"}
                                         :visibility/values ["Option1"]}})])

(def max-length-field-example
  [(merge label-field {:field/title {:en "The following field types can have a max length."
                                     :fi "Seuraavilla kenttätyypeillä voi olla pituusrajoitus."
                                     :sv "De nästa fälttyperna kan ha bengränsat längd."}})
   (merge text-field {:field/title {:en "Text field with max length"
                                    :fi "Tekstikenttä pituusrajalla"
                                    :sv "Textfält med begränsat längd"}
                      :field/optional true
                      :field/max-length 10})
   (merge texta-field {:field/title {:en "Text area with max length"
                                     :fi "Tekstialue pituusrajalla"
                                     :sv "Textområdet med begränsat längd"}
                       :field/optional true
                       :field/max-length 100})])

(def all-field-types-example
  (concat [label-field
           description-field
           text-field
           texta-field
           header-field
           (assoc date-field :field/optional true)
           (assoc email-field :field/optional true)
           (assoc attachment-field :field/optional true)]
          conditional-field-example ; array of fields
          [(assoc multiselect-field :field/optional true)
           (assoc table-field :field/optional true)]
          max-length-field-example ; array of fields
          [(assoc phone-number-field :field/optional true)
           (assoc ip-address-field :field/optional true)]))

(deftest test-all-field-types-example
  (is (= (:vs (:field/type schema/FieldTemplate))
         (set (map :field/type all-field-types-example)))
      "a new field has been added to schema but not to this test data"))

(defn- range-1
  "Like `clojure.core/range`, but starts from 1 and `end` is inclusive."
  [end]
  (range 1 (inc end)))

(defn- in-parallel [fs]
  (let [executor (Executors/newFixedThreadPool 10)]
    (try
      (->> fs
           (.invokeAll executor)
           (map #(.get ^Future %))
           doall)
      (finally
        (.shutdownNow executor)))))

(def ^:private vocabulary (-> "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut non diam vel erat dapibus facilisis vel vitae nunc. Curabitur at fermentum lorem. Cras et bibendum ante. Etiam convallis erat justo. Phasellus cursus molestie vehicula. Etiam molestie tellus vitae consectetur dignissim. Pellentesque euismod hendrerit mi sed tincidunt. Integer quis lorem ut ipsum egestas hendrerit. Aenean est nunc, mattis euismod erat in, sodales rutrum mauris. Praesent sit amet risus quis felis congue ultricies. Nulla facilisi. Sed mollis justo id tristique volutpat.\n\nPhasellus augue mi, facilisis ac velit et, pharetra tristique nunc. Pellentesque eget arcu quam. Curabitur dictum nulla varius hendrerit varius. Proin vulputate, ex lacinia commodo varius, ipsum velit viverra est, eget molestie dui nisi non eros. Nulla lobortis odio a magna mollis placerat. Interdum et malesuada fames ac ante ipsum primis in faucibus. Integer consectetur libero ut gravida ullamcorper. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Donec aliquam feugiat mollis. Quisque massa lacus, efficitur vel justo vel, elementum mollis magna. Maecenas at sem sem. Praesent sed ex mattis, egestas dui non, volutpat lorem. Nulla tempor, nisi rutrum accumsan varius, tellus elit faucibus nulla, vel mattis lacus justo at ante. Sed ut mollis ex, sed tincidunt ex.\n\nMauris laoreet nibh eget erat tincidunt pharetra. Aenean sagittis maximus consectetur. Curabitur interdum nibh sed tincidunt finibus. Sed blandit nec lorem at iaculis. Morbi non augue nec tortor hendrerit mollis ut non arcu. Suspendisse maximus nec ligula a efficitur. Etiam ultrices rhoncus leo quis dapibus. Integer vel rhoncus est. Integer blandit varius auctor. Vestibulum suscipit suscipit risus, sit amet venenatis lacus iaculis a. Duis eu turpis sit amet nibh sagittis convallis at quis ligula. Sed eget justo quis risus iaculis lacinia vitae a justo. In hac habitasse platea dictumst. Maecenas euismod et lorem vel viverra.\n\nDonec bibendum nec ipsum in volutpat. Vivamus in elit venenatis, venenatis libero ac, ultrices dolor. Morbi quis odio in neque consequat rutrum. Suspendisse quis sapien id sapien fermentum dignissim. Nam eu est vel risus volutpat mollis sed quis eros. Proin leo nulla, dictum id hendrerit vitae, scelerisque in elit. Proin consectetur sodales arcu ac tristique. Suspendisse ut elementum ligula, at rhoncus mauris. Aliquam lacinia at diam eget mattis. Phasellus quam leo, hendrerit sit amet mi eget, porttitor aliquet velit. Proin turpis ante, consequat in enim nec, tempus consequat magna. Vestibulum fringilla ac turpis nec malesuada. Proin id lectus iaculis, suscipit erat at, volutpat turpis. In quis faucibus elit, ut maximus nibh. Sed egestas egestas dolor.\n\nNulla varius orci quam, id auctor enim ultrices nec. Morbi et tellus ac metus sodales convallis sed vehicula neque. Pellentesque rhoncus mattis massa a bibendum. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Fusce tincidunt nulla non aliquet facilisis. Praesent nisl nisi, finibus id odio sed, consectetur feugiat mauris. Suspendisse sed lacinia ligula. Duis vitae nisl leo. Donec erat arcu, feugiat sit amet sagittis ac, scelerisque nec est. Pellentesque finibus mauris nulla, in maximus sapien pharetra vitae. Sed leo elit, consequat eu aliquam vitae, feugiat ut eros. Pellentesque dictum feugiat odio sed commodo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin neque quam, varius vel libero sit amet, rhoncus sollicitudin ex. In a dui non neque malesuada pellentesque.\n\nProin tincidunt nisl non commodo faucibus. Sed porttitor arcu neque, vitae bibendum sapien placerat nec. Integer eget tristique orci. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Donec eu molestie eros. Nunc iaculis rhoncus enim, vel mattis felis fringilla condimentum. Interdum et malesuada fames ac ante ipsum primis in faucibus. Aenean ac augue nulla. Phasellus vitae nulla lobortis, mattis magna ac, gravida ipsum. Aenean ornare non nunc non luctus. Aenean lacinia lectus nec velit finibus egestas vel ut ipsum. Cras hendrerit rhoncus erat, vel maximus nunc.\n\nPraesent quis imperdiet quam. Praesent ligula tellus, consectetur sed lacus eu, malesuada condimentum tellus. Donec et diam hendrerit, dictum diam quis, aliquet purus. Suspendisse pulvinar neque at efficitur iaculis. Nulla erat orci, euismod id velit sed, dictum hendrerit arcu. Nulla aliquam molestie aliquam. Duis et semper nisi, eget commodo arcu. Praesent rhoncus, nulla id sodales eleifend, ante ipsum pellentesque augue, id iaculis sem est vitae est. Phasellus cursus diam a lorem vestibulum sodales. Nullam lacinia tortor vel tellus commodo, sit amet sodales quam malesuada.\n\nNulla tempor lectus vel arcu feugiat, vel dapibus ex dapibus. Maecenas purus justo, aliquet et sem sit amet, tincidunt venenatis dui. Nulla eget purus id sapien elementum rutrum eu vel libero. Cras non accumsan justo posuere.\n\n"
                              str/lower-case
                              (str/split #"[( \n)+]")
                              distinct
                              sort
                              rest))

(defn- random-long-string [& [n]]
  (str (str/join " " (repeatedly (or n 1000) #(rand-nth vocabulary)))
       ;; prevent string interning, just to be sure
       (UUID/randomUUID)))

(defn create-performance-test-data! []
  (binding [context/*test-users* +fake-users+
            context/*test-user-data* +fake-user-data+]
    (log/info "Creating performance test data")
    (let [resource-count 1000
          application-count 3000
          user-count 1000
          users (getx-test-users)
          user-data (getx-test-user-data)
          handlers [(:approver1 users) (:approver2 users)]
          owner (:owner users)
          _perf (rems.service.organizations/add-organization! {:organization/id "perf"
                                                               :organization/name {:fi "Suorituskykytestiorganisaatio" :en "Performance Test Organization" :sv "Organisationen för utvärderingsprov"}
                                                               :organization/short-name {:fi "Suorituskyky" :en "Performance" :sv "Uvärderingsprov"}
                                                               :organization/owners [{:userid (:organization-owner1 users)}]
                                                               :organization/review-emails []})
          workflow-id (test-helpers/create-workflow! {:actor owner
                                                      :organization {:organization/id "perf"}
                                                      :title "Performance tests"
                                                      :handlers handlers})
          form-id (test-helpers/create-form! {:actor owner
                                              :organization {:organization/id "perf"}
                                              :form/internal-name "Performance tests"
                                              :form/external-title {:en "Performance tests EN"
                                                                    :fi "Performance tests FI"
                                                                    :sv "Performance tests SV"}
                                              :form/fields [(merge description-field {:field/title {:en "Project name"
                                                                                                    :fi "Projektin nimi"
                                                                                                    :sv "Projektets namn"}
                                                                                      :field/placeholder {:en "Project"
                                                                                                          :fi "Projekti"
                                                                                                          :sv "Projekt"}})
                                                            (merge texta-field {:field/title {:en "Project description"
                                                                                              :fi "Projektin kuvaus"
                                                                                              :sv "Projektets beskrivning"}
                                                                                :field/placeholder {:en "The purpose of the project is to..."
                                                                                                    :fi "Projektin tarkoitus on..."
                                                                                                    :sv "Det här projekt..."}})]})
          form (rems.service.form/get-form-template form-id)
          category {:category/id (test-helpers/create-category! {:actor owner
                                                                 :category/title {:en "Performance"
                                                                                  :fi "Suorituskyky"
                                                                                  :sv "Prestand"}
                                                                 :category/description {:en "These catalogue items are for performance test."
                                                                                        :fi "Nämä resurssit ovat suorituskykytestausta varten."
                                                                                        :sv "Dessa resurser är för prestand."}})}
          license-id (test-helpers/create-license! {:actor owner
                                                    :license/type :text
                                                    :organization {:organization/id "perf"}
                                                    :license/title {:en "Performance License"
                                                                    :fi "Suorituskykylisenssi"
                                                                    :sv "Licens för prestand"}
                                                    :license/text {:en "Be fast."
                                                                   :fi "Ole nopea."
                                                                   :sv "Var snabb."}})
          cat-item-ids (vec (in-parallel
                             (for [n (range-1 resource-count)]
                               (fn []
                                 ;; rebinding due to thread
                                 (binding [context/*test-users* users
                                           context/*test-user-data* user-data]
                                   (let [resource-id (test-helpers/create-resource! {:organization {:organization/id "perf"}
                                                                                     :license-ids [license-id]})]
                                     (test-helpers/create-catalogue-item! {:actor owner
                                                                           :title {:en (str "Performance test resource " n)
                                                                                   :fi (str "Suorituskykytestiresurssi " n)
                                                                                   :sv (str "Licens för prestand " n)}
                                                                           :resource-id resource-id
                                                                           :form-id form-id
                                                                           :organization {:organization/id "perf"}
                                                                           :workflow-id workflow-id
                                                                           :categories [category]})))))))
          user-ids (vec (in-parallel
                         (for [n (range-1 user-count)]
                           (fn []
                             ;; rebinding due to thread
                             (binding [context/*test-users* users
                                       context/*test-user-data* user-data]
                               (let [user-id (str "perftester" n)]
                                 (rems.db.users/add-user-raw! user-id {:userid user-id
                                                                       :email (str user-id "@example.com")
                                                                       :name (str "Performance Tester " n)})
                                 user-id))))))]
      (with-redefs [rems.config/env (assoc rems.config/env :enable-save-compaction false)] ; generate more events without compaction
        (in-parallel
         (for [n (range-1 application-count)]
           (fn []
             ;; rebinding due to thread
             (binding [context/*test-users* users
                       context/*test-user-data* user-data]
               (log/info "Creating performance test application" n "/" application-count)
               (let [cat-item-id (rand-nth cat-item-ids)
                     user-id (rand-nth user-ids)
                     handler (rand-nth handlers)
                     app-id (test-helpers/create-application! {:catalogue-item-ids [cat-item-id]
                                                               :actor user-id})
                     long-answer (random-long-string)]
                 (dotimes [i 20] ; user saves ~ 20 times while writing an application
                   (test-helpers/command! {:type :application.command/save-draft
                                           :application-id app-id
                                           :actor user-id
                                           :field-values [{:form form-id
                                                           :field (:field/id (first (:form/fields form)))
                                                           :value (str "Performance test application " (UUID/randomUUID))}
                                                          {:form form-id
                                                           :field (:field/id (second (:form/fields form)))
                                                       ;; 1000 words of lorem ipsum samples from a text from www.lipsum.com
                                                       ;; to increase the memory requirements of an application
                                                           :value (subs long-answer 0 (int (/ (* (inc i) (count long-answer)) (inc i))))}]}))
                 (test-helpers/command! {:type :application.command/accept-licenses
                                         :application-id app-id
                                         :actor user-id
                                         :accepted-licenses [license-id]})
                 (test-helpers/command! {:type :application.command/submit
                                         :application-id app-id
                                         :actor user-id})
                 (test-helpers/command! {:type :application.command/approve
                                         :application-id app-id
                                         :actor handler
                                         :comment ""})))))))
      (log/info "Performance test applications created"))))

(defn create-organizations! []
  (let [owner (getx-test-users :owner)
        organization-owner1 (getx-test-users :organization-owner1)
        organization-owner2 (getx-test-users :organization-owner2)]
    {:default (test-helpers/create-organization! {:actor owner})
     :hus (test-helpers/create-organization! {:actor owner
                                              :organization/id "hus"
                                              :organization/name {:fi "Helsingin yliopistollinen sairaala"
                                                                  :en "Helsinki University Hospital"
                                                                  :sv "Helsingfors Universitetssjukhus"}
                                              :organization/short-name {:fi "HUS"
                                                                        :en "HUS"
                                                                        :sv "HUS"}
                                              :organization/owners [{:userid organization-owner1}]
                                              :organization/review-emails []})
     :thl (test-helpers/create-organization! {:actor owner
                                              :organization/id "thl"
                                              :organization/name {:fi "Terveyden ja hyvinvoinnin laitos"
                                                                  :en "Finnish institute for health and welfare"
                                                                  :sv "Institutet för hälsa och välfärd"}
                                              :organization/short-name {:fi "THL"
                                                                        :en "THL"
                                                                        :sv "THL"}
                                              :organization/owners [{:userid organization-owner2}]
                                              :organization/review-emails []})
     :nbn (test-helpers/create-organization! {:actor owner
                                              :organization/id "nbn"
                                              :organization/name {:fi "NBN"
                                                                  :en "NBN"
                                                                  :sv "NBN"}
                                              :organization/short-name {:fi "NBN"
                                                                        :en "NBN"
                                                                        :sv "NBN"}
                                              :organization/owners [{:userid organization-owner2}]
                                              :organization/review-emails []})
     :abc (test-helpers/create-organization! {:actor owner
                                              :organization/id "abc"
                                              :organization/name {:fi "ABC"
                                                                  :en "ABC"
                                                                  :sv "ABC"}
                                              :organization/short-name {:fi "ABC"
                                                                        :en "ABC"
                                                                        :sv "ABC"}
                                              :organization/owners []
                                              :organization/review-emails [{:name {:fi "ABC Kirjaamo"}
                                                                            :email "kirjaamo@abc.efg"}]})
     :csc (test-helpers/create-organization! {:actor owner
                                              :organization/id "csc"
                                              :organization/name {:fi "CSC – TIETEEN TIETOTEKNIIKAN KESKUS OY"
                                                                  :en "CSC – IT CENTER FOR SCIENCE LTD."
                                                                  :sv "CSC – IT CENTER FOR SCIENCE LTD."}
                                              :organization/short-name {:fi "CSC"
                                                                        :en "CSC"
                                                                        :sv "CSC"}
                                              :organization/owners []
                                              :organization/review-emails []})
     :organization-1 (test-helpers/create-organization! {:actor owner
                                                         :organization/id "organization1"
                                                         :organization/name {:fi "Organization 1"
                                                                             :en "Organization 1"
                                                                             :sv "Organization 1"}
                                                         :organization/short-name {:fi "ORG 1"
                                                                                   :en "ORG 1"
                                                                                   :sv "ORG 1"}
                                                         :organization/owners [{:userid organization-owner1}]
                                                         :organization/review-emails []})
     :organization-2 (test-helpers/create-organization! {:actor owner
                                                         :organization/id "organization2"
                                                         :organization/name {:fi "Organization 2"
                                                                             :en "Organization 2"
                                                                             :sv "Organization 2"}
                                                         :organization/short-name {:fi "ORG 2"
                                                                                   :en "ORG 2"
                                                                                   :sv "ORG 2"}
                                                         :organization/owners [{:userid organization-owner2}]
                                                         :organization/review-emails []})}))

(defn- create-applications!
  [{:keys [catalogue-items]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant1)
        approver (getx-test-users :approver1)
        reviewer (getx-test-users :reviewer)]
    (test-helpers/create-draft! applicant [(:default catalogue-items)] "draft application")

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "applied")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant}))

    (let [time (time/minus (time/now) (time/days 7))
          app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "old applied" time)]
      (test-helpers/command! {:time time
                              :type :application.command/submit
                              :application-id app-id
                              :actor applicant}))

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "approved with comment")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/request-review
                              :application-id app-id
                              :actor approver
                              :reviewers [reviewer]
                              :comment "please have a look"})
      (test-helpers/command! {:type :application.command/review
                              :application-id app-id
                              :actor reviewer
                              :comment "looking good"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id app-id
                              :actor approver
                              :comment "Thank you! Approved!"}))

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "rejected")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/reject
                              :application-id app-id
                              :actor approver
                              :comment "Never going to happen"}))

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "approved & closed")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/request-review
                              :application-id app-id
                              :actor approver
                              :reviewers [reviewer]
                              :comment "please have a look"})
      (test-helpers/command! {:type :application.command/review
                              :application-id app-id
                              :actor reviewer
                              :comment "looking good"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id app-id
                              :actor approver
                              :comment "Thank you! Approved!"})
      (test-helpers/command! {:type :application.command/close
                              :application-id app-id
                              :actor approver
                              :comment "Research project complete, closing."}))

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "waiting for review")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/request-review
                              :application-id app-id
                              :actor approver
                              :reviewers [reviewer]
                              :comment ""}))

    (let [app-id (test-helpers/create-draft! applicant [(:default catalogue-items)] "waiting for decision")]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/request-decision
                              :application-id app-id
                              :actor approver
                              :deciders [reviewer]
                              :comment ""}))))

(defn- create-anonymized-handling-items!
  [{:keys [categories
           forms
           licenses
           resources]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant1)
        handler (getx-test-users :approver1)
        handler-2 (getx-test-users :approver2)
        reviewer (getx-test-users :reviewer)
        decider (getx-test-users :decider)
        owner (getx-test-users :owner)
        rejecter-bot (getx-test-users :rejecter-bot)
        restricted-workflow (test-helpers/create-workflow! {:actor owner
                                                            :organization {:organization/id "nbn"}
                                                            :title "Restricted workflow"
                                                            :type :workflow/default
                                                            :handlers [handler handler-2 rejecter-bot]
                                                            :licenses [(:workflow-link licenses)
                                                                       (:workflow-text licenses)]
                                                            :disable-commands [{:command :application.command/invite-member}
                                                                               {:command :application.command/close
                                                                                :when/state [:application.state/returned]
                                                                                :when/role [:applicant]}]
                                                            :anonymize-handling true})
        cat-restricted (test-helpers/create-catalogue-item! {:actor owner
                                                             :title {:en "Default workflow (restricted)"
                                                                     :fi "Oletustyövuo (rajoitettu)"
                                                                     :sv "Standard arbetsflöde (begränsad)"}
                                                             :infourl {:en "http://www.google.com"
                                                                       :fi "http://www.google.fi"
                                                                       :sv "http://www.google.se"}
                                                             :resource-id (:res1 resources)
                                                             :form-id (:form forms)
                                                             :organization {:organization/id "nbn"}
                                                             :workflow-id restricted-workflow
                                                             :categories [(:special categories)]})
        app-id (test-helpers/create-draft! applicant [cat-restricted] "returned")]
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor applicant})
    (test-helpers/command! {:type :application.command/remark
                            :application-id app-id
                            :actor handler
                            :comment "processing is due now"
                            :public true})
    (test-helpers/command! {:type :application.command/request-review
                            :application-id app-id
                            :actor handler-2
                            :reviewers [reviewer]
                            :comment "please have a look"})
    (test-helpers/command! {:type :application.command/remark
                            :application-id app-id
                            :actor reviewer
                            :comment "application is missing key purpose"
                            :public true})
    (test-helpers/command! {:type :application.command/review
                            :application-id app-id
                            :actor reviewer
                            :comment "not looking good in my opinion"})
    (test-helpers/command! {:type :application.command/request-decision
                            :application-id app-id
                            :actor handler-2
                            :comment "please decide"
                            :deciders [decider]})
    (test-helpers/command! {:type :application.command/remark
                            :application-id app-id
                            :actor decider
                            :comment "i agree with previous remarker"
                            :public true})
    (test-helpers/command! {:type :application.command/decide
                            :application-id app-id
                            :actor decider
                            :decision :rejected
                            :comment "unacceptable in current state"})
    (test-helpers/command! {:type :application.command/return
                            :application-id app-id
                            :actor handler-2
                            :comment "need more details"})))

(defn- create-expiring-draft-applications!
  [{:keys [catalogue-items]
    :as shared-test-data}]
  (doseq [n (range 80 100 2)
          :let [created-at (time/minus (time/now) (time/days n))]]
    (test-helpers/create-draft! (getx-test-users :applicant1)
                                [(:default catalogue-items)]
                                "forgotten draft"
                                created-at)))

(defn- create-bona-fide-items! []
  (let [owner (getx-test-users :owner)
        bot (getx-test-users :bona-fide-bot)
        res (test-helpers/create-resource! {:resource-ext-id "bona-fide"
                                            :organization {:organization/id "default"}
                                            :actor owner})
        form (test-helpers/create-form! {:actor owner
                                         :form/internal-name "Bona Fide form"
                                         :form/external-title {:en "Form"
                                                               :fi "Lomake"
                                                               :sv "Blankett"}
                                         :organization {:organization/id "default"}
                                         :form/fields [(assoc email-field :field/title {:fi "Suosittelijan sähköpostiosoite"
                                                                                        :en "Referer's email address"
                                                                                        :sv "sv"})]})
        wf (test-helpers/create-workflow! {:actor owner
                                           :organization {:organization/id "default"}
                                           :title "Bona Fide workflow"
                                           :type :workflow/default
                                           :handlers [bot]})]
    (test-helpers/create-catalogue-item! {:actor owner
                                          :organization {:organization/id "default"}
                                          :title {:en "Apply for Bona Fide researcher status"
                                                  :fi "Hae Bona Fide tutkija -statusta"
                                                  :sv "sv"}
                                          :resource-id res
                                          :form-id form
                                          :workflow-id wf})))

(defn- create-disabled-applications!
  [{:keys [categories
           forms
           resources
           workflows]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant2)
        handler (getx-test-users :approver1)
        owner (getx-test-users :owner)
        cat-default-disabled (test-helpers/create-catalogue-item! {:actor owner
                                                                   :title {:en "Default workflow (disabled)"
                                                                           :fi "Oletustyövuo (pois käytöstä)"
                                                                           :sv "Standard arbetsflöde (avaktiverat)"}
                                                                   :resource-id (:res1 resources)
                                                                   :form-id (:form forms)
                                                                   :organization {:organization/id "nbn"}
                                                                   :workflow-id (:default workflows)
                                                                   :categories [(:ordinary categories)]})
        _draft-app (test-helpers/create-draft! applicant [cat-default-disabled] "draft with disabled item")
        submitted-app (test-helpers/create-draft! applicant [cat-default-disabled] "submitted application with disabled item")
        approved-app (test-helpers/create-draft! applicant [cat-default-disabled] "approved application with disabled item")]
    (test-helpers/command! {:type :application.command/submit
                            :application-id submitted-app
                            :actor applicant})
    (test-helpers/command! {:type :application.command/submit
                            :application-id approved-app
                            :actor applicant})
    (test-helpers/command! {:type :application.command/approve
                            :application-id approved-app
                            :actor handler
                            :comment "Looking good"})
    (db/set-catalogue-item-enabled! {:id cat-default-disabled
                                     :enabled false})))

(defn- create-expired-catalogue-item!
  [{:keys [categories
           resources
           workflows]
    :as shared-test-data}]
  (let [owner (getx-test-users :owner)
        default-expired (test-helpers/create-catalogue-item! {:actor owner
                                                              :title {:en "Default workflow (expired)"
                                                                      :fi "Oletustyövuo (vanhentunut)"
                                                                      :sv "Standard arbetsflöde (utgånget)"}
                                                              :resource-id (:res1 resources)
                                                              :form-id (:form resources)
                                                              :organization {:organization/id "nbn"}
                                                              :workflow-id (:default workflows)
                                                              :categories [(:ordinary categories)]})]
    (db/set-catalogue-item-endt! {:id default-expired :end (time/now)})))

(defn- create-private-form-items!
  "Demo catalo 
   - forms with public and private fields, and catalogue items and applications using them"
  [{:keys [categories
           forms
           resources
           workflows]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant1)
        handler (getx-test-users :approver2)
        reviewer (getx-test-users :reviewer)
        owner (getx-test-users :owner)
        catid-1 (test-helpers/create-catalogue-item! {:actor owner
                                                      :title {:en "Default workflow with public and private fields"
                                                              :fi "Testityövuo julkisilla ja yksityisillä lomakekentillä"
                                                              :sv "Standard arbetsflöde med publika och privata textfält"}
                                                      :resource-id (:res1 resources)
                                                      :form-id (:form-with-public-and-private-fields forms)
                                                      :organization {:organization/id "nbn"}
                                                      :workflow-id (:default workflows)
                                                      :categories [(:ordinary categories)]})
        catid-2 (test-helpers/create-catalogue-item! {:actor owner
                                                      :title {:en "Default workflow with private form"
                                                              :fi "Oletustyövuo yksityisellä lomakkeella"
                                                              :sv "Standard arbetsflöde med privat blankett"}
                                                      :resource-id (:res2 resources)
                                                      :form-id (:form-private-nbn forms)
                                                      :organization {:organization/id "nbn"}
                                                      :workflow-id (:default workflows)
                                                      :categories [(:ordinary categories)]})
        app-id (test-helpers/create-draft! applicant [catid-1 catid-2] "two-form draft application")]
    (test-helpers/invite-and-accept-member! {:actor applicant
                                             :application-id app-id
                                             :member (getx-test-user-data :applicant2)})
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor applicant})
    (test-helpers/command! {:type :application.command/request-review
                            :application-id app-id
                            :actor handler
                            :reviewers [reviewer]
                            :comment "please have a look"})))

(defn- create-duo-items!
  "Create resources, catalogue items and example application for demoing DUO"
  [{:keys [categories
           forms
           workflows]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant1)
        handler (getx-test-users :approver2)
        reviewer (getx-test-users :reviewer)
        owner (getx-test-users :owner)
        duo-resource-1 (test-helpers/create-resource!
                        {:resource-ext-id "Eyelid melanoma samples"
                         :organization {:organization/id "nbn"}
                         :actor owner
                         :resource/duo {:duo/codes [{:id "DUO:0000007"
                                                     :restrictions [{:type :mondo
                                                                     :values [{:id "MONDO:0000928"}]}]}
                                                    {:id "DUO:0000015"}
                                                    {:id "DUO:0000019"}
                                                    {:id "DUO:0000027"
                                                     :restrictions [{:type :project
                                                                     :values [{:value "project name here"}]}]
                                                     :more-info {:en "List of approved projects can be found at http://www.google.fi"}}]}})
        duo-resource-2 (test-helpers/create-resource!
                        {:resource-ext-id "Spinal cord melanoma samples"
                         :organization {:organization/id "nbn"}
                         :actor owner
                         :resource/duo {:duo/codes [{:id "DUO:0000007"
                                                     :restrictions [{:type :mondo
                                                                     :values [{:id "MONDO:0001893"}]}]}
                                                    {:id "DUO:0000019"}
                                                    {:id "DUO:0000027"
                                                     :restrictions [{:type :project
                                                                     :values [{:value "project name here"}]}]
                                                     :more-info {:en "This DUO code is optional but recommended"}}]}})
        cat-id (test-helpers/create-catalogue-item! {:actor owner
                                                     :title {:en "Apply for eyelid melanoma dataset (EN)"
                                                             :fi "Apply for eyelid melanoma dataset (FI)"
                                                             :sv "Apply for eyelid melanoma dataset (SV)"}
                                                     :resource-id duo-resource-1
                                                     :form-id (:form forms)
                                                     :organization {:organization/id "nbn"}
                                                     :workflow-id (:default workflows)
                                                     :categories [(:special categories)]})
        cat-id-2 (test-helpers/create-catalogue-item! {:actor owner
                                                       :title {:en "Apply for spinal cord melanoma dataset (EN)"
                                                               :fi "Apply for spinal cord melanoma dataset (FI)"
                                                               :sv "Apply for spinal cord melanoma dataset (SV)"}
                                                       :resource-id duo-resource-2
                                                       :form-id (:form forms)
                                                       :organization {:organization/id "nbn"}
                                                       :workflow-id (:default workflows)
                                                       :categories [(:special categories)]})
        app-id (test-helpers/create-draft! applicant [cat-id-2] "draft application with DUO codes")
        app-id-2 (test-helpers/create-draft! applicant [cat-id] "application with DUO codes")]
    (test-helpers/command! {:type :application.command/save-draft
                            :application-id app-id
                            :actor applicant
                            :field-values []
                            :duo-codes [{:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0000928"}]}]}]})
    (test-helpers/command! {:type :application.command/save-draft
                            :application-id app-id-2
                            :actor applicant
                            :field-values []
                            :duo-codes [{:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0000928"}]}]}
                                        {:id "DUO:0000015"}
                                        {:id "DUO:0000019"}
                                        {:id "DUO:0000027" :restrictions [{:type :project :values [{:value "my project"}]}]}]})
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id-2
                            :actor applicant})
    (test-helpers/command! {:type :application.command/request-review
                            :application-id app-id-2
                            :actor handler
                            :reviewers [reviewer]
                            :comment "please have a look"})))

(defn create-attachment-redaction-items!
  "TODO:
   - create application to demo attachment redaction feature"
  [{:keys [categories
           workflows]
    :as shared-test-data}]
  (let [applicant (getx-test-users :applicant1)
        decider (getx-test-users :decider)
        handler (getx-test-users :approver2) ; "handler"
        handler2 (getx-test-users :approver1) ; "developer"
        reviewer (getx-test-users :reviewer)
        owner (getx-test-users :owner)
        form-id (test-helpers/create-form! {:actor owner
                                            :organization {:organization/id "nbn"}
                                            :form/internal-name "Redaction test form"
                                            :form/external-title {:en "Form"
                                                                  :fi "Lomake"
                                                                  :sv "Blankett"}
                                            :form/fields [{:field/type :description
                                                           :field/title {:en "Application title field"
                                                                         :fi "Hakemuksen otsikko -kenttä"
                                                                         :sv "Ansökningens rubrikfält"}
                                                           :field/optional false}
                                                          {:field/type :attachment
                                                           :field/title {:en "Attachment"
                                                                         :fi "Liitetiedosto"
                                                                         :sv "Bilaga"}
                                                           :field/optional false}]})
        resource-id (test-helpers/create-resource! {:resource-ext-id "Attachment redaction test"
                                                    :organization {:organization/id "nbn"}
                                                    :actor owner})
        cat-id (test-helpers/create-catalogue-item! {:actor owner
                                                     :title {:en "Complicated data request (EN)"
                                                             :fi "Complicated data request (FI)"
                                                             :sv "Complicated data request (SV)"}
                                                     :resource-id resource-id
                                                     :form-id form-id
                                                     :organization {:organization/id "nbn"}
                                                     :workflow-id (:decider workflows)
                                                     :categories [(:special categories)]})
        app-id (test-helpers/create-draft! applicant [cat-id] "redacted attachments")]
    (test-helpers/invite-and-accept-member! {:actor applicant
                                             :application-id app-id
                                             :member (getx-test-user-data :applicant2)})
    (test-helpers/fill-form! {:application-id app-id
                              :actor applicant
                              :field-value "complicated application with lots of attachments and five special characters \"åöâīē\""
                              :attachment (test-helpers/create-attachment! {:actor applicant
                                                                            :application-id app-id
                                                                            :filename "applicant_attachment.pdf"})})
    ;; (delete-orphan-attachments-on-submit) process manager removes all dangling attachments,
    ;; so we submit application first before creating more attachments
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor applicant})
    (test-helpers/command! {:type :application.command/request-review
                            :application-id app-id
                            :actor handler
                            :reviewers [reviewer]
                            :comment "please have a look. see attachment for details"
                            :attachments [{:attachment/id (test-helpers/create-attachment! {:actor handler
                                                                                            :application-id app-id
                                                                                            :filename (str "handler_" (random-long-string 5) ".pdf")})}]})
    (test-helpers/command! {:type :application.command/review
                            :application-id app-id
                            :actor reviewer
                            :comment "here are my thoughts. see attachments for details"
                            :attachments [{:attachment/id (test-helpers/create-attachment! {:actor reviewer
                                                                                            :application-id app-id
                                                                                            :filename "reviewer_attachment.pdf"})}]})
    (let [handler2-attachments (vec (for [att ["process_document_one.pdf" "process_document_two.pdf" "process_document_three.pdf"]]
                                      {:attachment/id (test-helpers/create-attachment! {:actor handler2
                                                                                        :application-id app-id
                                                                                        :filename att})}))]
      (test-helpers/command! {:type :application.command/remark
                              :application-id app-id
                              :actor handler2
                              :comment "see the attached process documents"
                              :public true
                              :attachments handler2-attachments})
      (test-helpers/command! {:type :application.command/request-decision
                              :application-id app-id
                              :actor handler
                              :comment "please decide, here are my final notes"
                              :deciders [decider]
                              :attachments [{:attachment/id (test-helpers/create-attachment! {:actor handler
                                                                                              :application-id app-id
                                                                                              :filename "handler_attachment.pdf"})}]})
      (test-helpers/command! {:type :application.command/remark
                              :application-id app-id
                              :actor decider
                              :comment "thank you, i will make my decision soon"
                              :public false
                              :attachments [{:attachment/id (test-helpers/create-attachment! {:actor decider
                                                                                              :application-id app-id
                                                                                              :filename "decider_attachment.pdf"})}]})
      (test-helpers/command! {:type :application.command/redact-attachments
                              :application-id app-id
                              :actor handler
                              :comment "updated the process documents to latest version"
                              :public true
                              :redacted-attachments (vec (rest handler2-attachments))
                              :attachments [{:attachment/id (test-helpers/create-attachment! {:actor handler
                                                                                              :application-id app-id
                                                                                              :filename "process_document_two.pdf"})}
                                            {:attachment/id (test-helpers/create-attachment! {:actor handler
                                                                                              :application-id app-id
                                                                                              :filename "process_document_three.pdf"})}]}))))

(defn- create-items! []
  (let [approver1 (getx-test-users :approver1)
        approver2 (getx-test-users :approver2)
        approver-bot (getx-test-users :approver-bot)
        rejecter-bot (getx-test-users :rejecter-bot)
        owner (getx-test-users :owner)
        organization-owner1 (getx-test-users :organization-owner1)
        license1 (test-helpers/create-license! {:actor owner
                                                :license/type :link
                                                :organization {:organization/id "nbn"}
                                                :license/title {:en "Demo license"
                                                                :fi "Demolisenssi"
                                                                :sv "Demolicens"}
                                                :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                               :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                               :sv "https://www.apache.org/licenses/LICENSE-2.0"}})
        license2 (test-helpers/create-license! {:actor owner
                                                :license/type :link
                                                :organization {:organization/id "nbn"}
                                                :license/title {:en "Demo license 2"
                                                                :fi "Demolisenssi 2"
                                                                :sv "Demolicens 2"}
                                                :license/link {:en "https://fedoraproject.org/wiki/Licensing/Beerware"
                                                               :fi "https://fedoraproject.org/wiki/Licensing/Beerware"
                                                               :sv "https://fedoraproject.org/wiki/Licensing/Beerware"}})
        extra-license (test-helpers/create-license! {:actor owner
                                                     :license/type :link
                                                     :organization {:organization/id "nbn"}
                                                     :license/title {:en "Extra license"
                                                                     :fi "Ylimääräinen lisenssi"
                                                                     :sv "Extra licens"}
                                                     :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                                    :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                                    :sv "https://www.apache.org/licenses/LICENSE-2.0"}})
        license-organization-owner (test-helpers/create-license! {:actor organization-owner1
                                                                  :license/type :link
                                                                  :organization {:organization/id "organization1"}
                                                                  :license/title {:en "License owned by organization owner"
                                                                                  :fi "Lisenssi, jonka omistaa organisaatio-omistaja"
                                                                                  :sv "Licens som ägs av organisationägare"}
                                                                  :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                                                 :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                                                 :sv "https://www.apache.org/licenses/LICENSE-2.0"}})
        attachment-license (test-helpers/create-attachment-license! {:actor owner
                                                                     :organization {:organization/id "nbn"}})
        disabled-license (-> (test-helpers/create-license! {:actor owner
                                                            :license/type "link"
                                                            :organization {:organization/id "nbn"}
                                                            :license/title {:en "Disabled license"
                                                                            :fi "Käytöstä poistettu lisenssi"}
                                                            :license/link {:en "http://disabled"
                                                                           :fi "http://disabled"}})
                             (as-> id (do (db/set-license-enabled! {:id id :enabled false})
                                          id)))
        workflow-link-license (test-helpers/create-license! {:actor owner
                                                             :license/type :link
                                                             :organization {:organization/id "nbn"}
                                                             :license/title {:en "CC Attribution 4.0"
                                                                             :fi "CC Nimeä 4.0"
                                                                             :sv "CC Erkännande 4.0"}
                                                             :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                                            :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"
                                                                            :sv "https://creativecommons.org/licenses/by/4.0/legalcode.sv"}})
        workflow-text-license (test-helpers/create-license! {:actor owner
                                                             :license/type :text
                                                             :organization {:organization/id "nbn"}
                                                             :license/title {:en "General Terms of Use"
                                                                             :fi "Yleiset käyttöehdot"
                                                                             :sv "Allmänna villkor"}
                                                             :license/text {:en (apply str (repeat 10 "License text in English. "))
                                                                            :fi (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))
                                                                            :sv (apply str (repeat 10 "Licens på svenska. "))}})

        res1 (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                             :organization {:organization/id "nbn"}
                                             :actor owner})
        res2 (test-helpers/create-resource! {:resource-ext-id "Extra Data"
                                             :organization {:organization/id "nbn"}
                                             :actor owner
                                             :license-ids [license1]})
        res3 (test-helpers/create-resource! {:resource-ext-id "something else"
                                             :organization {:organization/id "hus"}
                                             :actor owner
                                             :license-ids [license1 extra-license attachment-license]})
        res-organization-owner (test-helpers/create-resource! {:resource-ext-id "Owned by organization owner"
                                                               :organization {:organization/id "organization1"}
                                                               :actor organization-owner1
                                                               :license-ids [license-organization-owner]})
        res-with-extra-license (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263"
                                                               :organization {:organization/id "nbn"}
                                                               :actor owner
                                                               :license-ids [extra-license attachment-license]})
        res-duplicate-resource-name1 (test-helpers/create-resource! {:resource-ext-id "duplicate resource name"
                                                                     :organization {:organization/id "hus"}
                                                                     :actor owner
                                                                     :license-ids [license1 extra-license attachment-license]})
        res-duplicate-resource-name2 (test-helpers/create-resource! {:resource-ext-id "duplicate resource name"
                                                                     :organization {:organization/id "hus"}
                                                                     :actor owner
                                                                     :license-ids [license2 extra-license attachment-license]})
        res-duplicate-resource-name-with-long-name1 (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263443773465837568375683683756"
                                                                                    :organization {:organization/id "hus"}
                                                                                    :actor owner
                                                                                    :license-ids [license1 extra-license attachment-license]})
        res-duplicate-resource-name-with-long-name2 (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263443773465837568375683683756"
                                                                                    :organization {:organization/id "hus"}
                                                                                    :actor owner
                                                                                    :license-ids [license2 extra-license attachment-license]})

        default-workflow (test-helpers/create-workflow! {:actor owner
                                                         :organization {:organization/id "nbn"}
                                                         :title "Default workflow"
                                                         :type :workflow/default
                                                         :handlers [approver1 approver2 rejecter-bot]
                                                         :licenses [workflow-link-license workflow-text-license]})
        decider-workflow (test-helpers/create-workflow! {:actor owner
                                                         :organization {:organization/id "nbn"}
                                                         :title "Decider workflow"
                                                         :type :workflow/decider
                                                         :handlers [approver1 approver2 rejecter-bot]
                                                         :licenses [workflow-link-license workflow-text-license]
                                                         :voting {:type :handlers-vote}
                                                         :processing-states [{:title {:en "In voting"
                                                                                      :fi "Äänestyksessä"
                                                                                      :sv "I omröstningen"}
                                                                              :value "in voting"}
                                                                             {:title {:en "Preliminarily approved"
                                                                                      :fi "Alustavasti hyväksytty"
                                                                                      :sv "Preliminärt godkänd"}
                                                                              :value "preliminarily approved"}]})
        master-workflow (test-helpers/create-workflow! {:actor owner
                                                        :organization {:organization/id "nbn"}
                                                        :title "Master workflow"
                                                        :type :workflow/master
                                                        :handlers [approver1 approver2 rejecter-bot]
                                                        :licenses [workflow-link-license workflow-text-license]})
        auto-approve-workflow (test-helpers/create-workflow! {:actor owner
                                                              :organization {:organization/id "nbn"}
                                                              :title "Auto-approve workflow"
                                                              :type :workflow/master
                                                              :handlers [approver-bot rejecter-bot]
                                                              :licenses [workflow-link-license workflow-text-license]})
        organization-owner-workflow (test-helpers/create-workflow! {:actor organization-owner1
                                                                    :organization {:organization/id "organization1"}
                                                                    :title "Owned by organization owner"
                                                                    :type :workflow/default
                                                                    :handlers [approver1 approver2 rejecter-bot]
                                                                    :licenses [license-organization-owner]})
        with-form-workflow (test-helpers/create-workflow! {:actor owner
                                                           :organization {:organization/id "nbn"}
                                                           :title "With workflow form"
                                                           :type :workflow/default
                                                           :handlers [approver1 approver2 rejecter-bot]
                                                           :licenses [workflow-link-license workflow-text-license]
                                                           :forms [{:form/id (test-helpers/create-form! {:actor owner
                                                                                                         :form/internal-name "Workflow form"
                                                                                                         :form/external-title {:en "Workflow form"
                                                                                                                               :fi "Työvuon lomake"
                                                                                                                               :sv "Blankett för arbetsflöde"}
                                                                                                         :organization {:organization/id "nbn"}
                                                                                                         :form/fields [description-field]})}]})

        form (test-helpers/create-form! {:actor owner
                                         :organization {:organization/id "nbn"}
                                         :form/internal-name "Example form with all field types"
                                         :form/external-title {:en "Example form with all field types"
                                                               :fi "Esimerkkilomake kaikin kenttätyypein"
                                                               :sv "Exempelblankett med alla fälttyper"}
                                         :form/fields all-field-types-example})
        form-public-and-private-fields (test-helpers/create-form! {:actor owner
                                                                   :organization {:organization/id "nbn"}
                                                                   :form/internal-name "Public and private fields form"
                                                                   :form/external-title {:en "Form"
                                                                                         :fi "Lomake"
                                                                                         :sv "Blankett"}
                                                                   :form/fields [(assoc text-field :field/max-length 100)
                                                                                 (merge text-field {:field/title {:en "Private text field"
                                                                                                                  :fi "Yksityinen tekstikenttä"
                                                                                                                  :sv "Privat textfält"}
                                                                                                    :field/max-length 100
                                                                                                    :field/privacy :private})]})
        form-private-nbn (test-helpers/create-form! {:actor owner
                                                     :organization {:organization/id "nbn"}
                                                     :form/internal-name "Simple form"
                                                     :form/external-title {:en "Form"
                                                                           :fi "Lomake"
                                                                           :sv "Blankett"}
                                                     :form/fields [(merge text-field {:field/max-length 100
                                                                                      :field/privacy :private})]})
        form-private-thl (test-helpers/create-form! {:actor owner
                                                     :organization {:organization/id "thl"}
                                                     :form/internal-name "Simple form"
                                                     :form/external-title {:en "Form"
                                                                           :fi "Lomake"
                                                                           :sv "Blankett"}
                                                     :form/fields [(merge text-field {:field/max-length 100
                                                                                      :field/privacy :private})]})
        form-private-hus (test-helpers/create-form! {:actor owner
                                                     :organization {:organization/id "hus"}
                                                     :form/internal-name "Simple form"
                                                     :form/external-title {:en "Form"
                                                                           :fi "Lomake"
                                                                           :sv "Blankett"}
                                                     :form/fields [(merge text-field {:field/max-length 100
                                                                                      :field/privacy :private})]})
        form-organization-owner (test-helpers/create-form! {:actor organization-owner1
                                                            :organization {:organization/id "organization1"}
                                                            :form/internal-name "Owned by organization owner"
                                                            :form/external-title {:en "Owned by organization owner"
                                                                                  :fi "Omistaja organization owner"
                                                                                  :sv "Ägare organization owner"}
                                                            :form/fields all-field-types-example})
        form-archived (-> (test-helpers/create-form! {:actor owner
                                                      :organization {:organization/id "nbn"}
                                                      :form/internal-name "Archived form, should not be seen by applicants"
                                                      :form/external-title {:en "Archived form, should not be seen by applicants"
                                                                            :fi "Archived form, should not be seen by applicants"
                                                                            :sv "Archived form, should not be seen by applicants"}})
                          (as-> id (do (with-user owner
                                         (rems.service.form/set-form-archived! {:id id :archived true}))
                                       id)))

        ordinary-category {:category/id (test-helpers/create-category! {:actor owner
                                                                        :category/title {:en "Ordinary"
                                                                                         :fi "Tavalliset"
                                                                                         :sv "Vanliga"}
                                                                        :category/description false})}
        technical-category {:category/id (test-helpers/create-category! {:actor owner
                                                                         :category/title {:en "Technical"
                                                                                          :fi "Tekniset"
                                                                                          :sv "Teknisk"}
                                                                         :category/description false})}

        special-category {:category/id (test-helpers/create-category! {:actor owner
                                                                       :category/title {:en "Special"
                                                                                        :fi "Erikoiset"
                                                                                        :sv "Speciellt"}
                                                                       :category/description {:en "Special catalogue items for demonstration purposes."
                                                                                              :fi "Erikoiset resurssit demoja varten."
                                                                                              :sv "Särskilda katalogposter för demonstration."}
                                                                       :category/children [technical-category]})}

        cat-master (test-helpers/create-catalogue-item! {:actor owner
                                                         :title {:en "Master workflow"
                                                                 :fi "Master-työvuo"
                                                                 :sv "Master-arbetsflöde"}
                                                         :infourl {:en "http://www.google.com"
                                                                   :fi "http://www.google.fi"
                                                                   :sv "http://www.google.se"}
                                                         :resource-id res1
                                                         :form-id form
                                                         :organization {:organization/id "nbn"}
                                                         :workflow-id master-workflow
                                                         :categories [technical-category]})
        cat-decider (test-helpers/create-catalogue-item! {:actor owner
                                                          :title {:en "Decider workflow"
                                                                  :fi "Päättäjätyövuo"
                                                                  :sv "Arbetsflöde för beslutsfattande"}
                                                          :infourl {:en "http://www.google.com"
                                                                    :fi "http://www.google.fi"
                                                                    :sv "http://www.google.se"}
                                                          :resource-id res1
                                                          :form-id form
                                                          :organization {:organization/id "nbn"}
                                                          :workflow-id decider-workflow
                                                          :categories [special-category]})
        cat-default (test-helpers/create-catalogue-item! {:actor owner
                                                          :title {:en "Default workflow"
                                                                  :fi "Oletustyövuo"
                                                                  :sv "Standard arbetsflöde"}
                                                          :infourl {:en "http://www.google.com"
                                                                    :fi "http://www.google.fi"
                                                                    :sv "http://www.google.se"}
                                                          :resource-id res1
                                                          :form-id form
                                                          :organization {:organization/id "nbn"}
                                                          :workflow-id default-workflow
                                                          :categories [ordinary-category]})
        cat-default-2 (test-helpers/create-catalogue-item! {:actor owner
                                                            :title {:en "Default workflow 2"
                                                                    :fi "Oletustyövuo 2"
                                                                    :sv "Standard arbetsflöde 2"}
                                                            :resource-id res2
                                                            :form-id form-private-thl
                                                            :organization {:organization/id "csc"}
                                                            :workflow-id default-workflow
                                                            :categories [ordinary-category]})
        cat-default-3 (test-helpers/create-catalogue-item! {:actor owner
                                                            :title {:en "Default workflow 3"
                                                                    :fi "Oletustyövuo 3"
                                                                    :sv "Standard arbetsflöde 3"}
                                                            :resource-id res3
                                                            :form-id form-private-hus
                                                            :organization {:organization/id "hus"}
                                                            :workflow-id default-workflow
                                                            :categories [ordinary-category]})
        cat-default-extra-license (test-helpers/create-catalogue-item! {:actor owner
                                                                        :title {:en "Default workflow with extra license"
                                                                                :fi "Oletustyövuo ylimääräisellä lisenssillä"
                                                                                :sv "Arbetsflöde med extra licens"}
                                                                        :resource-id res-with-extra-license
                                                                        :form-id form
                                                                        :organization {:organization/id "nbn"}
                                                                        :workflow-id default-workflow
                                                                        :categories [ordinary-category]})
        cat-auto-approve (test-helpers/create-catalogue-item! {:title {:en "Auto-approve workflow"
                                                                       :fi "Työvuo automaattisella hyväksynnällä"
                                                                       :sv "Arbetsflöde med automatisk godkänning"}
                                                               :infourl {:en "http://www.google.com"
                                                                         :fi "http://www.google.fi"
                                                                         :sv "http://www.google.se"}
                                                               :resource-id res1
                                                               :form-id form
                                                               :organization {:organization/id "nbn"}
                                                               :workflow-id auto-approve-workflow
                                                               :categories [special-category]})
        cat-organization-owner (test-helpers/create-catalogue-item! {:actor organization-owner1
                                                                     :title {:en "Owned by organization owner"
                                                                             :fi "Organisaatio-omistajan omistama"
                                                                             :sv "Ägas av organisationägare"}
                                                                     :resource-id res-organization-owner
                                                                     :form-id form-organization-owner
                                                                     :organization {:organization/id "organization1"}
                                                                     :workflow-id organization-owner-workflow
                                                                     :categories [special-category]})

        shared-test-data {:catalogue-items {:master cat-master
                                            :decider cat-decider
                                            :default cat-default
                                            :default-2 cat-default-2
                                            :default-3 cat-default-3
                                            :default-extra-license cat-default-extra-license
                                            :auto-approve cat-auto-approve
                                            :organization-owner cat-organization-owner}
                          :categories {:ordinary ordinary-category
                                       :technical technical-category
                                       :special special-category}
                          :forms {:form form
                                  :public-and-private-fields form-public-and-private-fields
                                  :private-nbn form-private-nbn
                                  :private-thl form-private-thl
                                  :private-hus form-private-hus
                                  :organization-owner form-organization-owner
                                  :archived form-archived}
                          :licenses {:license1 license1
                                     :license2 license2
                                     :extra-license extra-license
                                     :license-organization-owner license-organization-owner
                                     :attachment-license attachment-license
                                     :disabled disabled-license
                                     :workflow-link workflow-link-license
                                     :workflow-text workflow-text-license}
                          :resources {:res1 res1
                                      :res2 res2
                                      :res3 res3
                                      :res-organization-owner res-organization-owner
                                      :res-with-extra-license res-with-extra-license
                                      :res-duplicate-resource-name1 res-duplicate-resource-name1
                                      :res-duplicate-resource-name2 res-duplicate-resource-name2
                                      :res-duplicate-resource-name-with-long-name1 res-duplicate-resource-name-with-long-name1
                                      :res-duplicate-resource-name-with-long-name2 res-duplicate-resource-name-with-long-name2}
                          :workflows {:default default-workflow
                                      :decider decider-workflow
                                      :master master-workflow
                                      :auto-approve auto-approve-workflow
                                      :organization-owner organization-owner-workflow
                                      :with-form with-form-workflow}}]

    (create-applications! shared-test-data)
    (create-expiring-draft-applications! shared-test-data)
    (create-disabled-applications! shared-test-data)
    (create-bona-fide-items!)
    (create-expired-catalogue-item! shared-test-data) ; XXX: catalogue expiration not in use?
    (create-private-form-items! shared-test-data)
    (create-anonymized-handling-items! shared-test-data)
    (create-duo-items! shared-test-data)
    (create-attachment-redaction-items! shared-test-data)))

(def +test-api-key+ "42")

;; XXX: several tests depend on this function
(defn create-test-api-key! []
  (rems.db.api-key/add-api-key! +test-api-key+ {:comment "test data"}))

(defn create-demo-api-key! []
  (rems.db.api-key/add-api-key! 55 {:comment "Finna"}))

(defn create-users-and-roles! [& [{:keys [roles]}]]
  (let [users (getx-test-users)
        roles (or roles
                  {(:owner users) #{:owner}
                   (:reporter users) #{:reporter}
                   (:expirer-bot users) #{:expirer}})]
    (doseq [user (vals users)
            :let [attributes (getx-test-user-data user)]]
      (apply test-helpers/create-user!
             attributes
             (get roles user)))))

;; XXX: several tests depend on this function
(defn create-test-users-and-roles! []
  (binding [context/*test-users* +fake-users+
            context/*test-user-data* +fake-user-data+]
    (create-users-and-roles! {:roles {(getx-test-users :owner) #{:owner}
                                      (getx-test-users :reporter) #{:reporter}}}))
  (db/add-user! {:user "invalid" :userattrs nil}))

(defn create-test-data! [& [print-invocations?]]
  (binding [context/*test-users* (merge +bot-users+ +fake-users+)
            context/*test-user-data* (merge +bot-user-data+ +fake-user-data+)
            context/*print-test-invocations* (true? print-invocations?)]

    (test-helpers/assert-no-existing-data!)
    (create-test-api-key!)
    (create-users-and-roles!)
    (create-organizations!)
    (create-items!)))

(defn create-demo-data! [& [print-invocations?]]
  (binding [context/*test-users* (merge +bot-users+ (case (:authentication rems.config/env)
                                                      :oidc +oidc-users+
                                                      +demo-users+))
            context/*test-user-data* (merge +bot-user-data+ (case (:authentication rems.config/env)
                                                              :oidc +oidc-user-data+
                                                              +demo-user-data+))
            context/*print-test-invocations* (true? print-invocations?)]

    (test-helpers/assert-no-existing-data!)
    (create-demo-api-key!)
    (create-users-and-roles!)
    (create-organizations!)
    (create-items!)))

(comment
  (do ; you can manually re-create test data (useful sometimes when debugging)
    (luminus-migrations.core/migrate ["reset"] (select-keys rems.config/env [:database-url]))
    (create-test-data! true)
    (create-demo-data! true)
    (create-performance-test-data!)))
