(ns directionalsurvey.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [directionalsurvey.config :as config]
            [directionalsurvey.events :as events]
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
(defn gen-chart-config-handson
  [tableconfig]
  (let [currenttableconfig  tableconfig
        ret (atom {
                   :chart    {:type     "line"
                              :zoomType "xy"}
                   :title    {:text "Directional Survey"}
                   :subtitle {:text "An experiment"}
                   :xAxis    {:title      {:text "X"}}
                   :yAxis    {:title      {:text "Y"}
                              :reversed true}
                   :credits  {:enabled false}})]
    (let [tabledata (:data currenttableconfig)
          tmptabledata (into [[0 0 0]] tabledata)
          tmptabledata1 (mapv (fn [in]
                                (let [md (get in 0)
                                      tvd (get in 1)
                                      dev (get in 2)]
                                  [md tvd dev 0]))
                              tmptabledata)
          tmptable (reduce (fn [data rowIdx]
                             (let [md1 (get-in data [(- rowIdx 1) 0])
                                   md2 (get-in data [rowIdx 0])
                                   x1 (get-in data [(- rowIdx 1) 3])
                                   dev2 (get-in data [rowIdx 2])
                                   x2 (+ x1 (* (- md2 md1) (js/Math.sin (* (/ dev2 180.0) js/Math.PI))))]
                               (assoc-in data [rowIdx 3] x2)))
                           tmptabledata1
                           (range 1 (count tmptabledata1)))
          tmptable1 (rest tmptable)
          gendata (mapv (fn [data]
                          (let [y (get data 1)
                                x (get data 3)]
                            [x y]))
                        tmptable1)
          mydata [{:name "Directional survey" :data gendata}]]
      ;(println "currenttableconfig: " tabledata)
      ;(println "tmptabledata: " tmptabledata)
      ;(println "tmptabledata1: " tmptabledata1)
      ;(println "tmptable: " tmptable)
      ;(println "mydata: " mydata)
      (swap! ret assoc-in [:series] mydata))
    ret))

(defui MyGlobalChart
  Object
  (render [this]
    (dom/div
      #js {:style {:height "100%" :width "100%" :position "relative"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (gen-chart-config-handson tableconfig)]
      (swap! mydb/staticstates
             assoc
             :globalchart (js/Highcharts.Chart. (dom/node this) (clj->js @my-chart-config)))))
  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (gen-chart-config-handson tableconfig)
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

(defn ^:export init []
  (dev-setup))