(ns rems.translations-page
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.text :refer [text]]))

(rf/reg-event-db
 ::enter-page
 (fn [db _]
   (assoc db :prevent-grab-focus true)))

(rf/reg-sub
 ::get-translations
 :<- [:translations]
 :<- [:language]
 (fn [[translations language] _]
   (get-in translations [language :t])))

(rf/reg-sub
 ::root-keys
 :<- [::get-translations]
 (fn [translations _]
   (sort (keys translations))))

(rf/reg-sub
 ::get-children-by-key
 :<- [::get-translations]
 (fn [translations [_ ks]]
   (let [v (get-in translations ks)]
     (if (map? v)
       (sort (keys v))
       (list)))))

(rf/reg-sub
 ::get-translation-by-key
 :<- [::get-translations]
 (fn [translations [_ ks]]
   (let [v (get-in translations ks)]
     (if (map? v)
       (into (sorted-map) v)
       v))))

(def search-input (r/atom nil))

(defn search-keys []
  [:div.search-field.mt-3
   [:label.mr-3 {:for :translations-search}
    (text :t.search/search)]

   [:div.input-group.mr-2.w-50
    [:input.form-control
     {:id :translations-search
      :type :text
      :value @search-input
      :on-change (fn [event]
                   (let [value (-> event .-target .-value)
                         value (if (string? value)
                                 (clojure.string/triml value)
                                 value)]
                     (reset! search-input value)))}]]])

(defn root-key [key-path]
  (r/with-let [expand-keys? (r/atom false)]
    (let [children @(rf/subscribe [::get-children-by-key key-path])]
      [:div.container-fluid (when @expand-keys?
                              {:style {:border-left "1px solid #000"}})
       (cond
         (and (seq children) @expand-keys?)
         [:<>
          [:div.row
           [:div.col
            [:code.color-pre.pointer {:on-click #(r/rswap! expand-keys? not)}
             (last key-path)]]]
          [:div.row.my-2
           (into [:div.col.d-flex.flex-column.gap-1]
                 (for [k children]
                   [root-key (conj key-path k)]))]]

         (seq children)
         [:div.row
          [:div.col-sm-3
           [:code.color-pre.pointer {:on-click #(r/rswap! expand-keys? not)}
            (last key-path)]
           [:span.ml-2 "(" (count children) ")"]]]

         :else
         (let [value @(rf/subscribe [::get-translation-by-key key-path])]
           [:div.row
            [:div.col-sm-3
             [:code.color-pre (last key-path)]]
            [:div.col-sm-9
             (cond
               (and (string? value) (str/blank? value))
               [:p (pr-str "") [:b " <empty value>"]]

               (string? value)
               [:p (pr-str value)]

               :else
               [:pre (with-out-str
                       (pprint/pprint value))])]]))])))

(defn translations-page []
  [:div.container
   [atoms/document-title "Translations"]
   (into [:div.d-flex.flex-column.gap-1]
         (for [k @(rf/subscribe [::root-keys])]
           [root-key [k]]))])
