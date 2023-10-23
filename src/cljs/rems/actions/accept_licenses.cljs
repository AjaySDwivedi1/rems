(ns rems.actions.accept-licenses
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::send-accept-licenses
 (fn [_ [_ {:keys [application-id licenses on-finished]}]]
   (let [description [text :t.actions/accept-licenses]]
     (post! "/api/applications/accept-licenses"
            {:rems/request-id ::request-id
             :params {:application-id application-id
                      :accepted-licenses licenses}
             :handler (flash-message/default-success-handler
                       :accept-licenses description (fn [_] (on-finished)))
             :error-handler (flash-message/default-error-handler :accept-licenses description)}))
   {}))

(defn accept-licenses-action-button [application-id licenses on-finished]
  [atoms/rate-limited-button {:id "accept-licenses-button"
                              :text (text :t.actions/accept-licenses)
                              :class "btn-primary"
                              :disabled @(rf/subscribe [:rems.spa/pending-request ::request-id])
                              :on-click #(rf/dispatch [::send-accept-licenses {:application-id application-id
                                                                               :licenses licenses
                                                                               :on-finished on-finished}])}])
