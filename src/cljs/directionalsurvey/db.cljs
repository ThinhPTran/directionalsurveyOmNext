(ns directionalsurvey.db)


(def init-tableconfig {:colHeaders ["MD" "TVD" "Deviation"]
                       :data        [[0 0 0]
                                     [0 0 0]
                                     [0 0 0]]
                       :rowHeaders  false
                       :contextMenu true})

; reactive atom that manages our application state
(def staticstates (atom {:globaltable nil
                         :globalchart nil
                         :localtable nil
                         :localchart nil}))

(def globalconfig
  (atom {:tableconfig init-tableconfig}))

(def global-users
  (atom {:user/names nil}))

(def local-count
  (atom {:count 0}))

(def local-login
  (atom {:user/name nil
         :input-text "sample input"}))

(def global-states
  (atom {:name "Global cummulative states"
         :totalactions 0
         :currentpick 0
         :tableconfig init-tableconfig
         :totallistactions nil
         :listactions nil}))

(def local-states
  (atom {:name "Local user states"
         :tableconfig init-tableconfig
         :listactions nil}))