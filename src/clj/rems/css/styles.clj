(ns rems.css.styles
  "CSS stylesheets are generated by garden automatically when
  accessing the application on a browser. The garden styles can also
  be manually compiled by calling the function `rems.css.styles/screen-css`.

  For development purposes with live reload, the styles are rendered to
  `target/resources/public/css/:language/screen.css` whenever this ns is evaluated
  so that we can autoreload them."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [garden.color :as c]
            [garden.core :as g]
            [garden.selectors :as s]
            [garden.units :as u]
            [medley.core :refer [map-vals remove-vals]]
            [mount.core :as mount]
            [rems.css.bootstrap :as bootstrap]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.css.style-utils :refer [css-var css-url theme-getx theme-logo-getx]]
            [ring.util.response :as response]))

(defn- theme-styles []
  {:--theme-alert-danger-bgcolor (theme-getx :alert-danger-bgcolor)
   :--theme-alert-danger-bordercolor (theme-getx :alert-danger-bordercolor)
   :--theme-alert-danger-color (theme-getx :alert-danger-color)
   :--theme-alert-dark-bgcolor (theme-getx :alert-dark-bgcolor)
   :--theme-alert-dark-bordercolor (theme-getx :alert-dark-bordercolor)
   :--theme-alert-dark-color (theme-getx :alert-dark-color)
   :--theme-alert-info-bgcolor (theme-getx :alert-info-bgcolor)
   :--theme-alert-info-bordercolor (theme-getx :alert-info-bordercolor)
   :--theme-alert-info-color (theme-getx :alert-info-color)
   :--theme-alert-light-bgcolor (theme-getx :alert-light-bgcolor)
   :--theme-alert-light-bordercolor (theme-getx :alert-light-bordercolor)
   :--theme-alert-light-color (theme-getx :alert-light-color)
   :--theme-alert-primary-bgcolor (theme-getx :alert-primary-bgcolor)
  ;;  :--theme-alert-primary-bordercolor (theme-getx :alert-primary-bordercolor)
   :--theme-alert-primary-bordercolor (theme-getx :alert-primary-bordercolor :alert-primary-color)
   :--theme-alert-primary-color (theme-getx :alert-primary-color)
   :--theme-alert-secondary-bgcolor (theme-getx :alert-secondary-bgcolor)
   :--theme-alert-secondary-bordercolor (theme-getx :alert-secondary-bordercolor)
   :--theme-alert-secondary-color (theme-getx :alert-secondary-color)
   :--theme-alert-success-bgcolor (theme-getx :alert-success-bgcolor)
   :--theme-alert-success-bordercolor (theme-getx :alert-success-bordercolor)
   :--theme-alert-success-color (theme-getx :alert-success-color)
   :--theme-alert-warning-bgcolor (theme-getx :alert-warning-bgcolor)
   :--theme-alert-warning-bordercolor (theme-getx :alert-warning-bordercolor)
   :--theme-alert-warning-color (theme-getx :alert-warning-color)

   :--theme-bg-danger (theme-getx :bg-danger)
   :--theme-bg-dark (theme-getx :bg-dark)
   :--theme-bg-info (theme-getx :bg-info)
   :--theme-bg-light (theme-getx :bg-light)
   :--theme-bg-primary (theme-getx :bg-primary)
   :--theme-bg-secondary (theme-getx :bg-secondary)
   :--theme-bg-success (theme-getx :bg-success)
   :--theme-bg-warning (theme-getx :bg-warning)
   :--theme-bg-white (theme-getx :bg-white)
   :--theme-big-navbar-text-transform (theme-getx :big-navbar-text-transform)
   :--theme-button-navbar-font-weight (theme-getx :button-navbar-font-weight)

   :--theme-collapse-bgcolor (theme-getx :collapse-bgcolor)
   :--theme-collapse-color (theme-getx :collapse-color)
   :--theme-collapse-shadow (theme-getx :collapse-shadow)
   :--theme-color1 (theme-getx :color1)
   :--theme-color2 (theme-getx :color2)
   :--theme-color3 (theme-getx :color3)
   :--theme-color4 (theme-getx :color4)

   :--theme-font-family (theme-getx :font-family)
   :--theme-footer-bgcolor (theme-getx :footer-bgcolor)
   :--theme-footer-color (theme-getx :footer-color)

   :--theme-header-border (theme-getx :header-border)
   :--theme-header-shadow (theme-getx :header-shadow)

   :--theme-link-color (theme-getx :link-color)
   :--theme-link-hover-color (theme-getx :link-hover-color)
   :--theme-logo-bgcolor (theme-getx :logo-bgcolor)
   :--theme-logo-content-origin (theme-getx :logo-content-origin)
   :--theme-logo-name (css-url (theme-logo-getx :logo-name))
   :--theme-logo-name-sm (css-url (theme-logo-getx :logo-name-sm))

   :--theme-nav-active-color (theme-getx :nav-active-color :color4)
   :--theme-nav-color (theme-getx :nav-color :link-color)
   :--theme-nav-hover-color (theme-getx :nav-hover-color :color4)
   :--theme-navbar-color (theme-getx :navbar-color)
   :--theme-navbar-logo-name (css-url (theme-logo-getx :navbar-logo-name))

   :--theme-phase-bgcolor (theme-getx :phase-bgcolor)
   :--theme-phase-bgcolor-active (theme-getx :phase-bgcolor-active)
   :--theme-phase-bgcolor-completed (theme-getx :phase-bgcolor-completed)
   :--theme-phase-color (theme-getx :phase-color)
   :--theme-phase-color-active (theme-getx :phase-color-active)
   :--theme-phase-color-completed (theme-getx :phase-color-completed)
   :--theme-primary-button-bgcolor (theme-getx :primary-button-bgcolor :color4)
   :--theme-primary-button-color (theme-getx :primary-button-color)
   :--theme-primary-button-hover-bgcolor (theme-getx :primary-button-hover-bgcolor :primary-button-bgcolor :color4)
   :--theme-primary-button-hover-color (theme-getx :primary-button-hover-color :primary-button-color)

   :--theme-secondary-button-bgcolor (theme-getx :secondary-button-bgcolor :color4)
   :--theme-secondary-button-color (theme-getx :secondary-button-color)
   :--theme-secondary-button-hover-bgcolor (theme-getx :secondary-button-hover-bgcolor :secondary-button-bgcolor :color4)
   :--theme-secondary-button-hover-color (theme-getx :secondary-button-hover-color :secondary-button-color :color4)

   :--theme-table-bgcolor (theme-getx :table-bgcolor)
   :--theme-table-border (theme-getx :table-border)
   :--theme-table-heading-bgcolor (theme-getx :table-heading-bgcolor)
   :--theme-table-heading-color (theme-getx :table-heading-color)
   :--theme-table-hover-bgcolor (theme-getx :table-hover-bgcolor :color2)
   :--theme-table-hover-color (theme-getx :table-hover-color :table-text-color)
   :--theme-table-selection-bgcolor (or (theme-getx :table-selection-bgcolor)
                                        (-> (theme-getx :table-hover-bgcolor :table-bgcolor :color3)
                                            (c/darken 15)))
   :--theme-table-shadow (theme-getx :table-shadow)
   :--theme-table-stripe-color (theme-getx :table-stripe-color)
   :--theme-table-text-color (theme-getx :table-text-color)

   :--theme-text-danger (theme-getx :text-danger)
   :--theme-text-dark (theme-getx :text-dark)
   :--theme-text-info (theme-getx :text-info)
   :--theme-text-light (theme-getx :text-light)
   :--theme-text-muted (theme-getx :text-muted)
   :--theme-text-primary (theme-getx :text-primary)
   :--theme-text-secondary (theme-getx :text-secondary)
   :--theme-text-success (theme-getx :text-success)
   :--theme-text-warning (theme-getx :text-warning)
   :--theme-text-white (theme-getx :text-white)})

#_(defn localized-styles []
    {:--theme-logo-name (theme-logo-getx :logo-name)
     :--theme-logo-name-sm (theme-logo-getx :logo-name-sm)
     :--theme-navbar-logo-name (theme-logo-getx :navbar-logo-name)})

;; (defn- logo-styles []
;;   (list
;;    [:.logo-menu {:height (css-var :--theme-logo-height-menu)
;;                  :background-color (theme-getx :logo-bgcolor)
;;                  :width "100px"
;;                  :padding-top "0px"
;;                  :padding-bottom "0px"}]
;;    [:.logo {:height (css-var :--theme-logo-height)
;;             :background-color (theme-getx :logo-bgcolor)
;;             :width "100%"
;;             :margin "0 auto"
;;             :padding "0 20px"
;;             :margin-bottom (u/em 1)}]
;;    [(s/descendant :.logo :.img)
;;     (s/descendant :.logo-menu :.img)
;;     {:height "100%"
;;      :background-color (theme-getx :logo-bgcolor)
;;      :-webkit-background-size :contain
;;      :-moz-o-background-size :contain
;;      :-o-background-size :contain
;;      :background-size :contain
;;      :background-repeat :no-repeat
;;      :background-position [[:center :center]]
;;      :background-origin (theme-getx :logo-content-origin)}]
;;    #_[(s/descendant :.logo :.img)
;;       {:background-image (css-url (theme-logo-getx :logo-name))}]
;;    [(s/descendant :.logo-menu :.img)
;;     {:background-image (css-url (theme-logo-getx :navbar-logo-name))}]
;;    (stylesheet/at-media {:max-width (bootstrap/media-md :min-width)}
;;                         (list
;;                          [(s/descendant :.logo :.img)
;;                           {:background-color (theme-getx :logo-bgcolor)
;;                            :background-image (css-url (theme-logo-getx :logo-name-sm))
;;                            :-webkit-background-size :contain
;;                            :-moz-background-size :contain
;;                            :-o-background-size :contain
;;                            :background-size :contain
;;                            :background-repeat :no-repeat
;;                            :background-position [[:center :center]]}]
;;                          [:.logo {:height (css-var :--theme-logo-height)}]
;;                          [:.logo-menu {:display "none"}]))))

#_(defn table-selection-bgcolor []
    (if-let [selection-bgcolor (theme-getx :table-selection-bgcolor)]
      selection-bgcolor
      (-> (theme-getx :table-hover-bgcolor :table-bgcolor :color3)
          (c/darken 15))))

(defn- remove-nil-vals
  "Recursively removes all keys with nil values from a map."
  [obj]
  (assert (not= "" obj))
  (cond
    (record? obj) obj
    (map? obj) (->> obj
                    (map-vals remove-nil-vals)
                    (remove-vals nil?)
                    not-empty)
    (vector? obj) (mapv remove-nil-vals obj)
    (seq? obj) (map remove-nil-vals obj)
    :else obj))

(deftest test-remove-nil-vals
  (testing "empty"
    (is (= nil
           (remove-nil-vals {}))))
  (testing "flat"
    (is (= nil
           (remove-nil-vals {:a nil})))
    (is (= {:a 1}
           (remove-nil-vals {:a 1})))
    (is (= {:a false}
           (remove-nil-vals {:a false})))
    (is (= {:a "#fff"}
           (remove-nil-vals {:a "#fff"}))))
  (testing "nested"
    (is (= nil
           (remove-nil-vals {:a {:b nil}})))
    (is (= {:a {:b 1}}
           (remove-nil-vals {:a {:b 1}}))))
  (testing "multiple keys"
    (is (= {:b 2}
           (remove-nil-vals {:a nil
                             :b 2}))))
  (testing "vectors"
    (is (vector? (remove-nil-vals [1])))
    (is (= []
           (remove-nil-vals [])))
    (is (= [:a]
           (remove-nil-vals [:a])))
    (is (= [:a nil]
           (remove-nil-vals [:a {}])))
    (is (= [:a {:b 1}]
           (remove-nil-vals [:a {:b 1}])))
    (is (= [:a nil]
           (remove-nil-vals [:a {:b nil}]))))
  (testing "lists"
    (is (seq? (remove-nil-vals '(1))))
    (is (= '()
           (remove-nil-vals '())))
    (is (= '(:a)
           (remove-nil-vals '(:a))))
    (is (= '(:a nil)
           (remove-nil-vals '(:a {})))))
  (testing "records"
    (is (= (u/px 10)
           (remove-nil-vals (u/px 10)))))
  (testing "empty strings are not supported"
    ;; CSS should not contain empty strings, but to be sure
    ;; that we don't accidentally break stuff, we don't convert
    ;; them to nil but instead throw an error.
    (is (thrown? AssertionError (remove-nil-vals {:a ""})))))

(defn build-variables []
  ;; TODO: set theme using some html attribute
  #_[(s/root (str "html[" :theme-default "]"))
     (->> (theme-styles)
          (into (sorted-map)))]
  [(s/root) (->> (theme-styles)
                 (into (sorted-map)))])

(defn build-screen []
  (list
   #_(bootstrap/reset-styles)

   ;; TODO: cannot be css-ified (yet)
   [:.rems-table
    (for [i (range 10)]
      [(str ".bg-depth-" i) {:background-color (str "rgba(0,0,0," (/ i 30.0) ")")}])
    (for [i (range 10)]
      [(str ".fs-depth-" i) {:font-size (str (format "%.2f" (+ 0.75 (Math/pow 2 (- i)))) "rem")}])
    (for [i (range 10)]
      [(str ".pad-depth-" i) {:padding-left (u/rem (* 1.8 i))}])]))

(defn- render-css-file [content {:keys [language filename]}]
  (let [dir-name (cond-> "target/resources/public/css"
                   language (str "/" (name language)))
        file-name (str dir-name "/" filename)
        dir (java.io.File. dir-name)]
    (log/info "Rendering CSS to file" (str (System/getProperty "user.dir") "/" file-name))
    (when-not (.exists dir)
      (.mkdirs dir))
    (spit file-name content)))

(defn screen-css []
  (g/css {:pretty-print? false} (remove-nil-vals (build-screen))))

(defn theme-css []
  (g/css {:pretty-print? false} (remove-nil-vals (build-variables))))

;; For development use and live reload, render all configured CSS
;; files so that the devtools will notice this change and force our app
;; to reload CSS files from the usual route.
;; The files are not used for anything besides this signaling.
(mount/defstate
  rendered-css-files
  :start
  (when (env :dev)
    (render-css-file (theme-css) {:filename "theme.css"})
    (doseq [language (env :languages)]
      (binding [context/*lang* language]
        (render-css-file (screen-css) {:language language
                                       :filename "screen.css"})))))

(defn render-screen-css
  "Helper function for rendering styles that has parameters for
  easy memoization purposes."
  [language]
  (log/info (str "Rendering stylesheet for language " language))
  (-> (screen-css)
      (response/response)
      (response/content-type "text/css")))

(defn render-theme-css []
  (log/info (str "Rendering theme variables stylesheet"))
  (-> (theme-css)
      (response/response)
      (response/content-type "text/css")))

(mount/defstate memoized-render-screen-css
  :start (memoize render-screen-css))

(mount/defstate memoized-render-theme-css
  :start (memoize render-theme-css))

(defroutes css-routes
  (GET "/css/:language/screen.css" [language]
    (binding [context/*lang* (keyword language)]
      (memoized-render-screen-css context/*lang*)))
  (GET "/css/theme.css" []
    (memoized-render-theme-css)))
