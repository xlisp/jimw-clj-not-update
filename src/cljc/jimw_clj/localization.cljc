(ns jimwclj.localization)

(def defaults
  {:page-title "Search notepad"
   :loading "Please wait while the app is loading"
   :tiko-msg "Search notepad"
   :new-msg "New MSG"})

(defn tr
  [k]
  (or (get defaults k)
      (str "Not yet localized: " k)))
