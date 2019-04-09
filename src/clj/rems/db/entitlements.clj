(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [clj-http.client :as http]
            [clojure.string :refer [join]]
            [clojure.tools.logging :as log]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.roles :refer [has-roles?]]
            [rems.text :as text]
            [rems.util :refer [getx-user-id]]))

;; TODO move Entitlement schema here from rems.api?

(defn- entitlement-to-api [{:keys [resid catappid start mail]}]
  {:resource resid
   :application-id catappid
   :start (text/localize-time start)
   :mail mail})

(defn get-entitlements-for-api [user-or-nil resource-or-nil]
  (if (has-roles? :handler)
    (mapv entitlement-to-api (db/get-entitlements {:user user-or-nil
                                                   :resource resource-or-nil}))
    (mapv entitlement-to-api (db/get-entitlements {:user (getx-user-id)
                                                   :resource resource-or-nil}))))

(defn get-entitlements-for-export
  "Returns a CSV string representing entitlements"
  []
  (when-not (has-roles? :handler)
    (throw-forbidden))
  (let [ents (db/get-entitlements)
        separator (:csv-separator env)]
    (with-out-str
      (println (join separator ["resource" "application" "user" "start"]))
      (doseq [e ents]
        (println (join separator [(:resid e) (:catappid e) (:userid e) (text/localize-time (:start e))]))))))

(defn- post-entitlements [target-key entitlements]
  (when-let [target (get-in env [:entitlements-target target-key])]
    (let [payload (for [e entitlements]
                    {:application (:catappid e)
                     :resource (:resid e)
                     :user (:userid e)
                     :mail (:mail e)})
          json-payload (json/generate-string payload)]
      (log/infof "Posting entitlements to %s:" target payload)
      (let [response (try
                       (http/post target
                                  {:throw-exceptions false
                                   :body json-payload
                                   :content-type :json
                                   :socket-timeout 2500
                                   :conn-timeout 2500})
                       (catch Exception e
                         (log/error "POST failed" e)
                         {:status "exception"}))
            status (:status response)]
        (when-not (= 200 status)
          (log/warnf "Post failed: %s", response))
        (db/log-entitlement-post! {:target target :payload json-payload :status status})))))

(defn- accepted-licenses? [application user-id]
  (every? (or (get (:application/accepted-licenses application)
                   user-id)
              #{})
          (map :license/id (:application/licenses application))))

(defn- add-entitlements-for
  "If the given application is approved, add an entitlement to the db
  and call the entitlement REST callback (if defined)."
  [application]
  (when (= :application.state/approved (:application/state application))
    (let [app-id (:application/id application)
          members (conj (map :userid (:application/members application))
                        (:application/applicant application))
          has-entitlement? (set (map :userid (db/get-entitlements {:application app-id})))
          members-to-update (->> members
                                 (filter #(accepted-licenses? application %))
                                 (remove has-entitlement?))]
      (when (seq members-to-update)
        (doseq [user-id members-to-update]
          (log/info "granting entitlements on application" app-id "to" user-id)
          (doseq [[resource-id ext-id] (->> (:application/resources application)
                                            (map (juxt :resource/id :resource/ext-id)))]
            (db/add-entitlement! {:application app-id
                                  :user user-id
                                  :resource resource-id})
            (post-entitlements :add (db/get-entitlements {:application app-id :user user-id :resource ext-id})))))
      members-to-update)))

(defn- end-entitlements-for
  [application]
  (when (= :application.state/closed (:application/state application))
    (let [app-id (:application/id application)
          members (conj (map :userid (:application/members application))
                        (:application/applicant application))]
      (log/info "ending entitlements on application" app-id "to" members)
      (db/end-entitlement! {:application app-id})
      (post-entitlements :remove (db/get-entitlements {:application app-id})))))

(defn update-entitlements-for
  [application]
  (add-entitlements-for application)
  (end-entitlements-for application))
