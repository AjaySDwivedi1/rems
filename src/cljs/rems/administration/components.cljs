(ns rems.administration.components
  "Reusable form components to use on the administration pages.

  Each component takes a `context` parameter to refer to the form state
  in the re-frame store. The context must be a map with these keys:
    :get-form     - Query ID for subscribing to the form data.
    :update-form  - Event handler ID for updating the form data.
                    The event will have two parameters `keys` and `value`,
                    analogous to the `assoc-in` parameters.

  The second parameter to each component is a map with all component specific variables.
  Typically this includes at least `keys` and `label`.
    :keys   - List of keys, a path to the component's data in the form state,
              analogous to the `get-in` and `assoc-in` parameters.
    :label  - String, shown to the user as-is."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.atoms :as atoms :refer [info-field textarea]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.common.roles :as roles]
            [rems.common.util :refer [clamp parse-int]]
            [rems.text :refer [localized text text-format]]))

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn- keys-to-id [keys]
  (->> keys
       (map key-to-id)
       (str/join "-")))

(defn- field-validation-message [error label]
  (when error
    [:div.invalid-feedback (text-format error label)]))

(defn input-field [{:keys [keys label placeholder context type normalizer readonly inline? input-style on-change] :as opts}]
  (let [id (keys-to-id keys)
        value @(rf/subscribe [(:get-form context) keys])
        error @(rf/subscribe [(:get-form-error context) keys])]
    [:div.form-group.field {:class (when inline? "row")}
     [:label {:for id
              :class (if inline?
                       "col-sm-auto col-form-label"
                       "administration-field-label")}
      label]
     [:div {:class (when inline? "col")}
      [:input.form-control (merge {:type type
                                   :id id
                                   :style input-style
                                   :disabled readonly
                                   :placeholder placeholder
                                   :class (when error "is-invalid")
                                   :value value
                                   :on-change (r/partial #(let [new-value (cond-> (.. % -target -value)
                                                                            (fn? normalizer) normalizer)]
                                                            (rf/dispatch-sync [(:update-form context) keys new-value])
                                                            (when (fn? on-change)
                                                              (on-change new-value))))}
                                  (select-keys opts [:min :max]))]
      [field-validation-message error label]]]))

(defn text-field
  "A basic text field, full page width."
  [context keys]
  [input-field (merge keys {:context context
                            :type "text"})])

(defn text-field-inline
  "A basic text field, label next to field"
  [context keys]
  [input-field (merge keys {:context context
                            :type "text"
                            :inline? true})])

(defn number-field
  "A basic number field, full page width."
  [context keys]
  [input-field (merge keys {:context context
                            :type "number"
                            :normalizer (r/partial #(some-> %
                                                            parse-int
                                                            (clamp (:min keys 0) (:max keys 1000000))))
                            :min 0
                            :max 1000000})])

(defn textarea-autosize
  "A basic textarea, full page width."
  [context {:keys [keys label placeholder normalizer on-change]}]
  (let [value @(rf/subscribe [(:get-form context) keys])
        error @(rf/subscribe [(:get-form-error context) keys])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:label.administration-field-label {:for id} label]
     [textarea {:id id
                :placeholder placeholder
                :value value
                :class (when error "is-invalid")
                :on-change (r/partial #(let [new-value (cond-> (.. % -target -value)
                                                         (fn? normalizer) normalizer)]
                                         (rf/dispatch-sync [(:update-form context) keys new-value])
                                         (when (fn? on-change)
                                           (on-change new-value))))}]
     [field-validation-message error label]]))

(defn localized-textarea-autosize
  "A textarea for inputting text in all supported languages.
  Has a separate textareas for each language. The data is stored
  in the form as a map of language to text. If `:localizations-key` is
  provided in opts, languages are mapped from `[:localizations lang localizations-key]`
  path."
  [context {:keys [keys localizations-key label placeholder normalizer on-change]}]
  (into [:div.form-group.localized-field
         [:label.administration-field-label label]]
        (for [language @(rf/subscribe [:languages])
              :let [key-path (if (some? localizations-key)
                               [:localizations language localizations-key]
                               (conj keys language))
                    value @(rf/subscribe [(:get-form context) key-path])
                    error @(rf/subscribe [(:get-form-error context) key-path])
                    id (keys-to-id (if (some? localizations-key)
                                     [:localizations language localizations-key]
                                     key-path))]]
          [:div.row.mb-0
           [:label.col-sm-1.col-form-label {:for id}
            (str/upper-case (name language))]
           [:div.col-sm-11
            [textarea {:id id
                       :placeholder placeholder
                       :value value
                       :class (when error "is-invalid")
                       :on-change (r/partial #(let [new-value (cond-> (.. % -target -value)
                                                                (fn? normalizer) normalizer)]
                                                (rf/dispatch-sync [(:update-form context) key-path new-value])
                                                (when (fn? on-change)
                                                  (on-change new-value))))}]
            [field-validation-message error label]]])))

(defn- localized-text-field-lang [context {:keys [keys-prefix label lang localizations-key normalizer on-change]}]
  (let [key-path (if localizations-key
                   [:localizations lang localizations-key]
                   (conj (vec keys-prefix) lang))
        value @(rf/subscribe [(:get-form context) key-path])
        error @(rf/subscribe [(:get-form-error context) key-path])
        id (keys-to-id key-path)]
    [:div.row.mb-0
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name lang))]
     [:div.col-sm-11
      [textarea {:id id
                 :min-rows 1
                 :value value
                 :class (when error "is-invalid")
                 :on-change (r/partial #(let [new-value (cond-> (.. % -target -value)
                                                          (fn? normalizer) normalizer)]
                                          (rf/dispatch-sync [(:update-form context) key-path new-value])
                                          (when (fn? on-change)
                                            (on-change new-value))))}]
      [field-validation-message error label]]]))

(defn localized-text-field
  "A text field for inputting text in all supported languages.
  Has a separate text fields for each language. The data is stored
  in the form as a map of language to text. If `:localizations-key` is
  provided in opts, languages are mapped from `[:localizations lang localizations-key]`
  path."
  [context {:keys [keys label localizations-key collapse? normalizer on-change]}]
  (let [id (keys-to-id (if (some? localizations-key)
                         [localizations-key]
                         keys))
        fields (for [lang @(rf/subscribe [:languages])]
                 [localized-text-field-lang context
                  {:keys-prefix keys
                   :label label
                   :lang lang
                   :localizations-key localizations-key
                   :normalizer normalizer
                   :on-change on-change}])]
    (if collapse?
      [:div.form-group.localized-field.mb-1
       [:label.administration-field-label
        label
        " "
        [collapsible/controls id]]
       (into [:div.collapse {:id id}]
             fields)]
      (into [:div.form-group.localized-field
             [:label.administration-field-label label]]
            fields))))

(defn event-checked [^js event]
  (.. event -target -checked))

(defn checkbox
  "A single checkbox, on its own line."
  [context {:keys [keys label negate? on-change]}]
  (let [id (keys-to-id keys)
        value @(rf/subscribe [(:get-form context) keys])
        val-fn (cond->> boolean
                 negate? (comp not))]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (val-fn value)
                                :on-change (r/partial #(let [new-value (val-fn (event-checked %))]
                                                         (rf/dispatch-sync [(:update-form context) keys new-value])
                                                         (when (fn? on-change)
                                                           (on-change new-value))))}]
      [:label.form-check-label {:for id}
       label]]]))

(defn- radio-button [context {:keys [keys value label orientation readonly on-change]}]
  (let [name (keys-to-id keys)
        id (keys-to-id (conj keys value))
        form-value @(rf/subscribe [(:get-form context) keys])
        error @(rf/subscribe [(:get-form-error context) keys])]
    [:div.form-check {:class (when (= :horizontal orientation)
                               "form-check-inline")}
     [:input.form-check-input {:id id
                               :type "radio"
                               :disabled readonly
                               :class (when error "is-invalid")
                               :name name
                               :value value
                               :checked (= value form-value)
                               :on-change (r/partial #(when (event-checked %)
                                                        (rf/dispatch-sync [(:update-form context) keys value])
                                                        (when (fn? on-change)
                                                          (on-change value))))}]
     [:label.form-check-label {:for id}
      label]]))

(defn radio-button-group
  "A list of radio buttons.
  `id`           - the id of the group
  `orientation`  - `:horizontal` or `:vertical`
  `keys`         - keys for options
  `label`        - optional label text for group
  `options`      - list of `{:value \"...\", :label \"...\"}`
  `readonly`     - boolean"
  [context {:keys [id keys label orientation options readonly on-change]}]
  [:div.form-group.field {:id id}
   (when label
     [:label.administration-field-label {:for id} label])
   (into [:div.form-control]
         (for [{:keys [value label]} options]
           ^{:key (str id "-" (name value))}
           [radio-button context {:keys keys
                                  :value value
                                  :label label
                                  :readonly readonly
                                  :orientation orientation
                                  :on-change on-change}]))])

(defn inline-info-field [text value & [opts]]
  [info-field text value (merge {:inline? true} opts)])

(defn localized-info-field
  "An info field for displaying text in all supported languages.
  The data is passed in as a map of language to text.
  If :localizations-key is passed in opts, language to text is
  mapped from `[:localizations lang localizations-key]` instead."
  [m {:keys [label localizations-key]}]
  (into [:<>]
        (for [lang @(rf/subscribe [:languages])]
          [inline-info-field (str label " (" (str/upper-case (name lang)) ")")
           (if (some? localizations-key)
             (get-in m [:localizations lang localizations-key])
             (get m lang))])))

(defn organization-field [context {:keys [keys readonly on-change]}]
  (let [on-update (r/partial (fn [new-value]
                               (rf/dispatch-sync [(:update-form context) keys new-value])
                               (when (fn? on-change)
                                 (on-change new-value))))
        id (keys-to-id keys)
        potential-value @(rf/subscribe [(:get-form context) keys])
        error @(rf/subscribe [(:get-form-error context) keys])
        owned-organizations @(rf/subscribe [:owned-organizations])
        valid-organizations (->> owned-organizations
                                 (into [] (comp (filter :enabled) (remove :archived))))
        disallowed (roles/disallow-setting-organization? @(rf/subscribe [:roles]))
          ;; if item was copied then this org could be something old
          ;; where we have no access to so reset here
        value (if (or readonly
                      disallowed
                      (contains? (set (mapv :organization/id valid-organizations))
                                 (:organization/id potential-value)))
                potential-value

                  ;; not accessible, reset
                (on-update nil))

        item-selected? #(= (:organization/id %) (:organization/id value))]
    [:div.form-group.field
     [:label.administration-field-label {:for id}
      (text :t.administration/organization)]
     (if (or readonly disallowed)
       [fields/readonly-field {:id id
                               :value (localized (:organization/name value))}]
       [dropdown/dropdown
        {:id id
         :items (->> valid-organizations
                     (mapv #(assoc % ::label (localized (:organization/name %)))))
         :item-key :organization/id
         :item-label ::label
         :item-selected? item-selected?
         :on-change on-update}])
     [field-validation-message error (text :t.administration/organization)]]))

(defn date-field
  [context {:keys [label keys min max validation optional normalizer on-change]}]
  (let [id (keys-to-id keys)
        value @(rf/subscribe [(:get-form context) keys])
        error @(rf/subscribe [(:get-form-error context) keys])]
      ;; TODO: format readonly value in user locale (give field-wrapper a formatted :value and :previous-value in opts)
    [:div.form-group.field
     [:label.administration-field-label {:for id} label]
     [:input.form-control {:type "date"
                           :id id
                           :name id
                           :class (when validation "is-invalid")
                           :value value
                           :required (not optional)
                           :aria-required (not optional)
                           :aria-invalid (when validation true)
                           :aria-describedby (when validation
                                               (str id "-error"))
                           :min min
                           :max max
                           :on-change (r/partial #(let [new-value (cond-> (.. % -target -value)
                                                                    (fn? normalizer) normalizer)]
                                                    (rf/dispatch-sync [(:update-form context) keys new-value])
                                                    (when (fn? on-change)
                                                      (on-change new-value))))}]
     [field-validation-message error label]]))

(defn perform-action-button [{:keys [loading?] :as props}]
  [atoms/rate-limited-button
   (-> props
       (dissoc (when (or loading? @(rf/subscribe [:rems.spa/any-pending-request?]))
                 :on-click)))])
