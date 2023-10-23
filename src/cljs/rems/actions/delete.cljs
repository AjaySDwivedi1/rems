(ns rems.actions.delete
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view command!]]
            [rems.atoms :as atoms]
            [rems.text :refer [text]]))

(def ^:private action-form-id "delete")

(rf/reg-event-fx
 ::send-delete
 (fn [_ [_ {:keys [application-id on-finished]}]]
   (command! :application.command/delete
             {:application-id application-id}
             {:description [text :t.actions/delete]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn delete-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/delete)}])

(defn delete-form [application-id on-finished]
  [action-form-view action-form-id
   (text :t.actions/delete)
   [[atoms/rate-limited-button {:id "delete"
                                :text (text :t.actions/delete)
                                :class "btn-danger"
                                :disabled @(rf/subscribe [:rems.spa/pending-request :application.command/delete])
                                :on-click #(rf/dispatch [::send-delete {:application-id application-id
                                                                        :on-finished on-finished}])}]]
   [:div
    (text :t.actions/delete-intro)]])
