(ns directionalsurvey.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [directionalsurvey.config :as config]
            [directionalsurvey.events :as events]
            [directionalsurvey.utils :as utils]
            [directionalsurvey.db :as mydb]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

;; User names
(defui UserItems
  Object
  (render [this]
    (let [{:keys [name]} (om/props this)]
      (dom/li nil (str name)))))

(def ui-useritems (om/factory UserItems {:keyfn :name}))

(defui UserNames
  Object
  (render [this]
    (let [{:keys [user/names]} (om/props this)]
      (dom/div nil
               (dom/h2 nil "User names: ")
               ;(dom/div nil (str (om/props this)))
               (apply dom/ul nil
                            (map #(ui-useritems {:react-key %
                                                 :name %})
                                 names))))))

(def usernamesreconciler
  (om/reconciler {:state mydb/global-users}))

(om/add-root! usernamesreconciler
              UserNames (gdom/getElement "usernames"))

;; Counter
;(defui Counter
;  Object
;  (render [this]
;    (let [{:keys [count]} (om/props this)]
;      (dom/div nil
;               (dom/h2 nil (str "Count: " count))
;               (dom/button
;                 #js {:onClick
;                      (fn [e]
;                        (swap! mydb/local-count update-in [:count] inc))}
;                 "Click me!")))))
;
;(def counterreconciler
;  (om/reconciler {:state mydb/local-count}))
;
;(om/add-root! counterreconciler
;              Counter (gdom/getElement "counter"))

;;; Handsontable

(defn mytableread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mytablemutate om/dispatch)

(defmethod mytablemutate `settablevalue
  [{:keys [state] :as env} key {:keys [changeDatas]}]
  {:action (fn []
             (.log js/console "mytablemutate: " changeDatas)
             (events/set-table-value changeDatas))})

(defui MyGlobalTable
  static om/IQuery
  (query [this]
    [:tableconfig])
  Object
  (render [this]
    (dom/div
      #js {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)]
      (swap! mydb/staticstates
             assoc
             :globaltable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            ;(.log js/console "change something!!!")
                                                                            ;(.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeDatas ~changeData})])))))))))

  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          table (:globaltable @mydb/staticstates)]
      ;(.log js/console "componentDidUpdate")
      (.destroy table)
      (swap! mydb/staticstates
             assoc
             :globaltable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            ;(.log js/console "change something!!!")
                                                                            ;(.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeDatas ~changeData})]))))))))))
(def myglobaltablereconciler
  (om/reconciler {:state mydb/global-states
                  :parser (om/parser {:read mytableread :mutate mytablemutate})}))

(om/add-root! myglobaltablereconciler
              MyGlobalTable (gdom/getElement "myglobaltable"))

;; Highchart

(defui MyGlobalChart
  Object
  (render [this]
    (dom/div
      #js {:style {:height "100%" :width "100%" :position "relative"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)]
      (swap! mydb/staticstates
             assoc
             :globalchart (js/Highcharts.Chart. (dom/node this) (clj->js @my-chart-config)))))
  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)
          chart (:globalchart @mydb/staticstates)]
      (.log js/console "My global chart componentDidUpdate")
      (.destroy chart)
      (swap! mydb/staticstates
             assoc
             :globalchart (js/Highcharts.Chart. (dom/node this) (clj->js @my-chart-config))))))


(def myglobalchartreconciler
  (om/reconciler {:state mydb/global-states}))
                  ;:parser (om/parser {:read mytableread :mutate mytablemutate})}))

(om/add-root! myglobalchartreconciler
              MyGlobalChart (gdom/getElement "myglobalchart"))



;;;; Handsontable
;
(defn mylocaltableread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mylocaltablemutate om/dispatch)

(defmethod mylocaltablemutate `settablevalue
  [{:keys [state] :as env} key {:keys [changeDatas]}]
  {:action (fn []
             (.log js/console "mytablemutate: " changeDatas)
             (events/set-table-value changeDatas))})

(defui MyLocalTable
  static om/IQuery
  (query [this]
    [:tableconfig])
  Object
  (render [this]
    (dom/div
      #js {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)]
      (swap! mydb/staticstates
             assoc
             :localtable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            ;(.log js/console "change something!!!")
                                                                            ;(.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeDatas ~changeData})])))))))))

  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          table (:localtable @mydb/staticstates)]
      ;(.log js/console "componentDidUpdate")
      (.destroy table)
      (swap! mydb/staticstates
             assoc
             :localtable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            ;(.log js/console "change something!!!")
                                                                            ;(.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeDatas ~changeData})]))))))))))
(def mylocaltablereconciler
  (om/reconciler {:state mydb/local-states
                  :parser (om/parser {:read mylocaltableread :mutate mylocaltablemutate})}))

(om/add-root! mylocaltablereconciler
              MyLocalTable (gdom/getElement "mylocaltable"))
;
;;; Highchart
;
(defui MyLocalChart
  Object
  (render [this]
    (dom/div
      #js {:style {:height "100%" :width "100%" :position "relative"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)]
      (swap! mydb/staticstates
             assoc
             :localchart (js/Highcharts.Chart. (dom/node this) (clj->js @my-chart-config)))))
  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)
          chart (:localchart @mydb/staticstates)]
      (.log js/console "My local chart componentDidUpdate")
      (.destroy chart)
      (swap! mydb/staticstates
             assoc
             :localchart (js/Highcharts.Chart. (dom/node this) (clj->js @my-chart-config))))))


(def mylocalchartreconciler
  (om/reconciler {:state mydb/local-states}))
;:parser (om/parser {:read mytableread :mutate mytablemutate})}))

(om/add-root! mylocalchartreconciler
              MyLocalChart (gdom/getElement "mylocalchart"))



(defn ^:export init []
  (dev-setup))