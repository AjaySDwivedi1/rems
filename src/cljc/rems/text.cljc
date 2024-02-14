(ns rems.text
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            #?(:clj [clj-time.core :as time]
               :cljs [cljs-time.core :as time])
            #?(:clj [clj-time.format :as time-format]
               :cljs [cljs-time.format :as time-format])
            #?(:cljs [cljs-time.coerce :as time-coerce])
            #?(:clj [rems.locales]
               :cljs [re-frame.core :as rf])
            #?(:cljs [reagent.core :as r])
            [rems.common.application-util :as application-util]
            #?(:clj [rems.context :as context])
            [rems.tempura]))

#?(:clj
   (defmacro with-language [lang & body]
     `(binding [rems.context/*lang* ~lang]
        (assert (keyword? ~lang) {:lang ~lang})
        ~@body)))

(defn- failsafe-fallback
  "Fallback for when loading the translations has failed."
  [k args]
  (pr-str (vec (if (= :t/missing k)
                 (first args)
                 (cons k args)))))

(defn text-format
  "Return the tempura translation for a given key and arguments:

   `(text-format :key 1 2)`"
  [k & args]
  #?(:clj (rems.tempura/tr rems.locales/translations
                           context/*lang*
                           [k :t/missing]
                           args)
     :cljs (rems.tempura/tr @(rf/subscribe [:translations])
                            @(rf/subscribe [:language])
                            [k :t/missing (failsafe-fallback k args)]
                            args)))

(defn text-format-fn
  "Creates function that calls text-format with `k` and `val-or-fns`. Any later args
  are transformed and applied."
  [k & vals-or-fns]
  (let [f (fn wrapped-text-format [& args]
            (apply text-format k (->> vals-or-fns
                                      (mapv #(cond-> % (fn? %) (apply args))))))]
    #?(:clj f
       :cljs (r/partial f))))

(defn text-format-map
  "Return the tempura translation for a given key and argument map:

   `(text-format-map :key {:a 1 :b 2})`

   Additional vector of keys can be given to create vector arguments from map,
   in which case resource compiler infers (from resource) which parameters to use:

   `(text-format-map :key {:a 1 :b 2} [:b :a])`"
  ([k arg-map] (text-format k arg-map))
  ([k arg-map arg-vec] (apply text-format k arg-map (for [k arg-vec]
                                                      (get arg-map k)))))

(defn text-no-fallback
  "Return the tempura translation for a given key. Additional fallback
  keys can be given but there is no default fallback text."
  [& ks]
  #?(:clj (rems.tempura/tr rems.locales/translations
                           context/*lang*
                           ks)
     :cljs (try
             (rems.tempura/tr @(rf/subscribe [:translations])
                              @(rf/subscribe [:language])
                              ks)
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (rems.tempura/tr rems.locales/translations
                           context/*lang*
                           (conj (vec ks) (text-format :t/missing (vec ks))))
     ;; NB: we can't call the text-no-fallback here as in CLJS
     ;; we can both call this as function or use as a React component
     :cljs (try
             (rems.tempura/tr @(rf/subscribe [:translations])
                              @(rf/subscribe [:language])
                              (conj (vec ks) (text-format :t/missing (vec ks))))
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn localized [m]
  (let [lang #?(:clj context/*lang*
                :cljs @(rf/subscribe [:language]))]
    (or (get m lang)
        (first (vals m)))))

;; TODO: replace usages of `get-localized-title` with `localized`
(defn get-localized-title [item language]
  (or (get-in item [:localizations language :title])
      (:title (first (vals (get item :localizations))))))

(def ^:private states
  {:application.state/draft :t.applications.states/draft
   :application.state/submitted :t.applications.states/submitted
   :application.state/approved :t.applications.states/approved
   :application.state/rejected :t.applications.states/rejected
   :application.state/closed :t.applications.states/closed
   :application.state/returned :t.applications.states/returned
   :application.state/revoked :t.applications.states/revoked})

(defn localize-state
  ([state]
   (text (get states state :t.applications.states/unknown)))
  ([state processing-state]
   (text-format :t.label/parens (localize-state state) (localized processing-state))))

(def ^:private todos
  {:new-application :t.applications.todos/new-application
   :no-pending-requests :t.applications.todos/no-pending-requests
   :resubmitted-application :t.applications.todos/resubmitted-application
   :waiting-for-decision :t.applications.todos/waiting-for-decision
   :waiting-for-review :t.applications.todos/waiting-for-review
   :waiting-for-your-decision :t.applications.todos/waiting-for-your-decision
   :waiting-for-your-review :t.applications.todos/waiting-for-your-review})

(defn localize-todo [todo]
  (if (nil? todo)
    ""
    (text (get todos todo :t.applications.todos/unknown))))

(defn time-format []
  (time-format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn time-format-with-seconds []
  (time-format/formatter "yyyy-MM-dd HH:mm:ss" (time/default-time-zone)))

(defn localize-time [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format) (time/to-default-time-zone time))))))

(defn localize-time-with-seconds
  "Localized datetime with second precision."
  [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format-with-seconds) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format-with-seconds) (time/to-default-time-zone time))))))

(defn localize-utc-date
  "For a given time instant, return the ISO date (yyyy-MM-dd) that it corresponds to in UTC."
  [time]
  #?(:clj (time-format/unparse (time-format/formatter "yyyy-MM-dd") time)
     :cljs (time-format/unparse (time-format/formatter "yyyy-MM-dd") (time-coerce/to-local-date time))))

(defn format-utc-datetime
  "For a given time instant, format it in UTC."
  [time]
  (time-format/unparse (time-format/formatters :date-time) time))

(deftest test-localize-utc-date []
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 1 1))))
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 23 59))))
  ;; [cl]js dates are always in UTC, so we can only test these for clj
  #?(:clj (do
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 23 59)
                                                                      (time/time-zone-for-offset 5)))))
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 1 1)
                                                                      (time/time-zone-for-offset -5))))))))

(def ^:private event-types
  {:application.event/applicant-changed :t.applications.events/applicant-changed
   :application.event/approved :t.applications.events/approved
   :application.event/attachments-redacted :t.applications.events/attachments-redacted
   :application.event/closed :t.applications.events/closed
   :application.event/review-requested :t.applications.events/review-requested
   :application.event/reviewed :t.applications.events/reviewed
   :application.event/copied-from :t.applications.events/copied-from
   :application.event/copied-to :t.applications.events/copied-to
   :application.event/created :t.applications.events/created
   :application.event/decided :t.applications.events/decided
   :application.event/decider-invited :t.applications.events/decider-invited
   :application.event/decider-joined :t.applications.events/decider-joined
   :application.event/decision-requested :t.applications.events/decision-requested
   :application.event/deleted :t.applications.events/deleted
   :application.event/draft-saved :t.applications.events/draft-saved
   :application.event/external-id-assigned :t.applications.events/external-id-assigned
   :application.event/expiration-notifications-sent :t.applications.events/expiration-notifications-sent
   :application.event/licenses-accepted :t.applications.events/licenses-accepted
   :application.event/licenses-added :t.applications.events/licenses-added
   :application.event/member-added :t.applications.events/member-added
   :application.event/member-invited :t.applications.events/member-invited
   :application.event/member-joined :t.applications.events/member-joined
   :application.event/member-removed :t.applications.events/member-removed
   :application.event/member-uninvited :t.applications.events/member-uninvited
   :application.event/processing-state-changed :t.applications.events/processing-state-changed
   :application.event/rejected :t.applications.events/rejected
   :application.event/remarked :t.applications.events/remarked
   :application.event/resources-changed :t.applications.events/resources-changed
   :application.event/returned :t.applications.events/returned
   :application.event/revoked :t.applications.events/revoked
   :application.event/reviewer-invited :t.applications.events/reviewer-invited
   :application.event/reviewer-joined :t.applications.events/reviewer-joined
   :application.event/submitted :t.applications.events/submitted
   :application.event/voted :t.applications.events/voted})

(defn localize-user
  "Returns localization for special user if possible. Otherwise returns formatted user."
  [user]
  (case (:userid user)
    "rems-handler" (text :t.roles/anonymous-handler)
    (application-util/get-member-name user)))

(defn localize-invitation [{:keys [name email]}]
  (str name " <" email ">"))

(defn localize-vote [vote]
  (let [vote-key (case (name vote)
                   "accept" :t.applications.voting.votes/accept
                   "empty" :t.applications.voting.votes/empty
                   "reject" :t.applications.voting.votes/reject
                   nil)]
    (text vote-key :t/unknown)))

(defn localize-processing-state [state]
  (let [state-key (case (name state)
                    "in-progress" :t.applications.processing-states/in-progress
                    "initial-approval" :t.applications.processing-states/initial-approval
                    nil)]
    (text state-key :t/unknown)))

(defn- get-event-params
  "Returns extra localization params for event, if any."
  [event]
  (case (:event/type event)
    :application.event/applicant-changed
    {:new-applicant (localize-user (:application/applicant event))}

    :application.event/created
    {:application-external-id (:application/external-id event)}

    :application.event/decider-invited
    {:invited-user (localize-invitation (:application/decider event))}

    :application.event/decision-requested
    {:requested-users (str/join ", " (mapv localize-user
                                           (:application/deciders event)))}

    :application.event/external-id-assigned
    {:application-external-id (:application/external-id event)}

    :application.event/member-added
    {:added-user (localize-user (:application/member event))}

    :application.event/member-invited
    {:invited-user (localize-invitation (:application/member event))}

    :application.event/member-removed
    {:removed-user (localize-user (:application/member event))}

    :application.event/member-uninvited
    {:uninvited-user (localize-invitation (:application/member event))}

    :application.event/processing-state-changed
    {:state (or (localized (:processing-state/title event))
                (:processing-state/value event))}

    :application.event/resources-changed
    {:catalogue-items (str/join ", " (mapv #(localized (:catalogue-item/title %))
                                           (:application/resources event)))}

    :application.event/reviewer-invited
    {:invited-user (localize-invitation (:application/reviewer event))}

    :application.event/review-requested
    {:requested-users (str/join ", " (mapv localize-user
                                           (:application/reviewers event)))}

    :application.event/voted
    {:vote (when-not (str/blank? (:vote/value event))
             (localize-vote (:vote/value event)))}

    nil))

(defn localize-decision [event]
  (b/cond
    :when-let [decision (:application/decision event)]

    :let [decision-localization (case decision
                                  :approved :t.applications.events/approved
                                  :rejected :t.applications.events/rejected)
          params {:event-actor (localize-user (:event/actor-attributes event))}]
    (text-format-map decision-localization
                     params
                     [:event-actor])))

(defn localize-event [event]
  (let [event-type (:event/type event)
        event-localization-key (get event-types event-type :t.applications.events/unknown)
        event-actor (localize-user (:event/actor-attributes event))
        params (get-event-params event)]
    (str (text-format-map event-localization-key
                          (merge {:event-actor event-actor} params)
                          (apply conj [:event-actor] (sort (keys params))))
         ;; conditional translations
         (case event-type
           :application.event/approved
           (when-let [end (:entitlement/end event)]
             (str " " (text-format :t.applications/entitlement-end (localize-utc-date end))))

           :application.event/attachments-redacted
           (when (seq (:event/attachments event))
             (str " " (text :t.applications/redacted-attachments-replaced)))

           nil))))

(defn localize-attachment
  "If attachment is redacted, return localized text for redacted attachment.
   Otherwise return value of :attachment/filename."
  [attachment]
  (let [filename (:attachment/filename attachment)]
    (cond
      (= :filename/redacted filename)
      (text :t.applications/attachment-filename-redacted)

      (:attachment/redacted attachment)
      (text-format :t.label/parens filename (text :t.applications/attachment-filename-redacted))

      :else filename)))

(def ^:private localized-roles
  {;; :api-key
   :applicant :t.roles/applicant
   :decider :t.roles/decider
   ;; :everyone-else
   ;; :expirer
   :handler :t.roles/handler
   ;; :logged-in
   :member :t.roles/member
   ;; :organization-owner
   ;; :owner
   :past-decider :t.roles/past-decider
   :past-reviewer :t.roles/past-reviewer
   ;; :reporter
   :reviewer :t.roles/reviewer
   ;; :user-owner
   })

(defn localize-role [role]
  (text (get localized-roles role) :t/unknown))

(def ^:private localized-commands
  {:application.command/accept-invitation :t.commands/accept-invitation
   :application.command/accept-licenses :t.commands/accept-licenses
   :application.command/add-licenses :t.commands/add-licenses
   :application.command/add-member :t.commands/add-member
   :application.command/approve :t.commands/approve
   :application.command/assign-external-id :t.commands/assign-external-id
   :application.command/change-applicant :t.commands/change-applicant
   :application.command/change-processing-state :t.commands/change-processing-state
   :application.command/change-resources :t.commands/change-resources
   :application.command/close :t.commands/close
   :application.command/copy-as-new :t.commands/copy-as-new
   ;; :application.command/create
   :application.command/decide :t.commands/decide
   :application.command/delete :t.commands/delete
   :application.command/invite-decider :t.commands/invite-decider
   :application.command/invite-member :t.commands/invite-member
   :application.command/invite-reviewer :t.commands/invite-reviewer
   :application.command/redact-attachments :t.commands/redact-attachments
   :application.command/reject :t.commands/reject
   :application.command/remark :t.commands/remark
   :application.command/remove-member :t.commands/remove-member
   :application.command/request-decision :t.commands/request-decision
   :application.command/request-review :t.commands/request-review
   :application.command/return :t.commands/return
   :application.command/review :t.commands/review
   :application.command/revoke :t.commands/revoke
   :application.command/save-draft :t.commands/save-draft
   ;; :application.command/send-expiration-notifications
   :application.command/submit :t.commands/submit
   :application.command/uninvite-member :t.commands/uninvite-member
   :application.command/vote :t.commands/vote})

(defn localize-command [command]
  (let [command-type (if (keyword? command)
                       command
                       (:type command))]
    (text (get localized-commands command-type) :t/unknown)))

(defn- translation-key? [x]
  (and (keyword? x)
       (or (str/starts-with? (namespace x) "t.")
           (= "t" (namespace x)))))

(defn text-label [label-type & args]
  (let [kw (case label-type
             :dash :t.label/dash
             :default :t.label/default
             :long :t.label/long
             :optional :t.label/optional
             :parens :t.label/parens)]
    (->> args
         (mapv #(cond-> % (translation-key? %) (text)))
         (apply text-format kw))))
