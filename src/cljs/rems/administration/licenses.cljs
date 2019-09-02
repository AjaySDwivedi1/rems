(ns rems.administration.licenses
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-licenses]
                 [:rems.table/reset]]}))

(rf/reg-event-db
 ::fetch-licenses
 (fn [db]
   (fetch "/api/licenses/" {:url-params {:disabled true
                                         :expired (::display-old? db)
                                         :archived (::display-old? db)}
                            :handler #(rf/dispatch [::fetch-licenses-result %])})
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::update-license
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/licenses/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (flash-message/status-update-handler description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler description)})
   {}))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-licenses]}))

(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-license []
  [atoms/link {:class "btn btn-primary"}
   "/#/administration/create-license"
   (text :t.administration/create-license)])

(defn- to-view-license [license-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/licenses/" license-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::licenses-table-rows
 (fn [_ _]
   [(rf/subscribe [::licenses])
    (rf/subscribe [:language])])
 (fn [[licenses language] _]
   (map (fn [license]
          {:key (:id license)
           :title {:value (get-localized-title license language)}
           :type {:value (:licensetype license)}
           :start (let [value (:start license)]
                    {:value value
                     :display-value (localize-time value)})
           :end (let [value (:end license)]
                  {:value value
                   :display-value (localize-time value)})
           :active (let [checked? (status-flags/active? license)]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-license (:id license)]
                           [status-flags/enabled-toggle license #(rf/dispatch [::update-license %1 %2 [::fetch-licenses]])]
                           [status-flags/archived-toggle license #(rf/dispatch [::update-license %1 %2 [::fetch-licenses]])]]}})
        licenses)))

(defn- licenses-list []
  (let [licenses-table {:id ::licenses
                        :columns [{:key :title
                                   :title (text :t.administration/licenses)}
                                  {:key :type
                                   :title (text :t.administration/type)}
                                  {:key :start
                                   :title (text :t.administration/created)}
                                  {:key :end
                                   :title (text :t.administration/end)}
                                  {:key :active
                                   :title (text :t.administration/active)
                                   :filterable? false}
                                  {:key :commands
                                   :sortable? false
                                   :filterable? false}]
                        :rows [::licenses-table-rows]
                        :default-sort-column :title}]
    [:div.mt-3
     [table/search licenses-table]
     [table/table licenses-table]]))

(defn licenses-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/licenses)]
         [flash-message/component]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-license]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [licenses-list]])))
