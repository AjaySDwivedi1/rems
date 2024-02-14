(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [join-fixtures]]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.service.dependencies :as dependencies]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.category :as category]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.test-data-users :refer [+fake-user-data+ +fake-users+]]
            [rems.service.test-data :as test-data]
            [rems.db.user-mappings :as user-mappings]
            [rems.locales]))

(defn reset-db-fixture [f]
  (try
    (f)
    (finally
      (migrations/migrate ["reset"] {:database-url (:test-database-url env)}))))

(defn restart-db-and-event-cache-fixture [f]
  (mount/stop) ;; during interactive development, app might be running when tests start. we need to tear it down
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.locales/translations
                         #'rems.db.core/*db*)
  (db/assert-test-database!)

  ;; these are db level caches and tests use db rollback
  ;; it's best for us to start from scratch here
  (applications/empty-injections-cache!)

  (migrations/migrate ["migrate"] {:database-url (:test-database-url env)})
  (mount/start #'rems.db.events/low-level-events-cache) ; needs DB to start
  (f)
  (mount/stop))

(defn test-data-users-fixture [f]
  (binding [context/*test-users* +fake-users+
            context/*test-user-data* +fake-user-data+]
    (f)))

(def test-db-fixture
  (join-fixtures [test-data-users-fixture
                  restart-db-and-event-cache-fixture]))

(defn search-index-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.application.search/search-index)
  (f))

(defn reset-caches-fixture [f]
  (try
    (mount/start #'applications/all-applications-cache)
    (f)
    (finally
      (applications/reset-cache!)
      (catalogue/reset-cache!)
      (category/reset-cache!)
      (dependencies/reset-cache!)
      (user-mappings/reset-cache!)
      (events/empty-event-cache!))))
(def +test-api-key+ test-data/+test-api-key+) ;; re-exported for convenience

(defn- create-owners!
  "Create an owner, two organization owners, and their organizations."
  []
  (let [users (test-helpers/getx-test-users)
        user-data (test-helpers/getx-test-user-data)
        owner (get users :owner)
        organization-owner1 (get users :organization-owner1)
        organization-owner2 (get users :organization-owner2)]
    (test-data/create-test-api-key!)
    (test-helpers/create-user! (get user-data owner) :owner)
    (test-helpers/create-user! (get user-data organization-owner1))
    (test-helpers/create-user! (get user-data organization-owner2))
    (test-helpers/create-organization! {:actor owner})
    (test-helpers/create-organization! {:actor owner
                                        :organization/id "organization1"
                                        :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                                        :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}
                                        :organization/owners [{:userid organization-owner1}]
                                        :organization/review-emails []})
    (test-helpers/create-organization! {:actor owner
                                        :organization/id "organization2"
                                        :organization/name {:fi "Organization 2" :en "Organization 2" :sv "Organization 2"}
                                        :organization/short-name {:fi "ORG 2" :en "ORG 2" :sv "ORG 2"}
                                        :organization/owners [{:userid organization-owner2}]
                                        :organization/review-emails []})))

(defn owners-fixture [f]
  (create-owners!)
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (events/empty-event-cache!) ; NB can't rollback this cache so reset
    (f)))
