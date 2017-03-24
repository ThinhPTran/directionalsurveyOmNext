(ns directionalsurvey.utils)

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