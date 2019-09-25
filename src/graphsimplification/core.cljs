(ns ^:figwheel-always graphsimplification.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [cljs.spec.alpha :as s]
            [goog.string :as gstring]
            [goog.functions :as gfunctions]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [graphsimplification.algo :as algo]))

; spec definitions
(s/def ::coordinate (s/coll-of number? :count 2))
(s/def ::coordinates (s/coll-of ::coordinate))

; configuration
(defonce epsilon-max 20.0)
(defonce graph-dimensions {:width 800 :height 350})

; helpers
(defn coords-bounds [coords]
  (let [use-when-cmp (fn [current-val new-val cmp-fn]
                       (if (nil? current-val)
                         new-val
                         (if (cmp-fn new-val current-val)
                           new-val
                           current-val)))]
    (reduce
     (fn [bounds coord] {:max-x (use-when-cmp (get bounds :max-x nil) (first coord) >)
                         :min-x (use-when-cmp (get bounds :min-x nil) (first coord) <)
                         :max-y (use-when-cmp (get bounds :max-y nil) (second coord) >)
                         :min-y (use-when-cmp (get bounds :min-y nil) (second coord) <)})
     {}
     coords)))

(defn humanize-bytes [bytes & [si?]]
  (let [unit (if si? 1000 1024)]
    (if (< bytes unit)
      (str bytes " B")
      (let [exp (int  (/ (Math/log bytes)
                         (Math/log unit)))
            pre (str (nth (if si? "kMGTPE" "KMGTPE") (dec exp)) (if-not si? "i"))]
        (gstring/format "%.1f %sB" (/ bytes (Math/pow unit exp)) pre)))))

(defn avg [sq]
  (if sq
    (/
     (apply + sq)
     (count sq))
    0))

(defn to-json-string [v]
  (.stringify js/JSON
              (clj->js v)))

(defn normalize-to-fit [width height coords]
  (let [bounds (coords-bounds coords)
        coords-height (-
                       (:max-y bounds)
                       (:min-y bounds))
        coords-width (- (:max-x bounds) (:min-x bounds))
        fill-percent 0.9
        scale-factor (min
                      (/ (* width fill-percent) coords-width)
                      (/ (* height fill-percent) coords-height))]
    (map (fn [coord]
           [(+ (* (first coord) scale-factor)
               (* 0.5
                  (- width
                     (* coords-width scale-factor))))

            ; y takes into account the upside down y axis of the svg coordinate system
            (+
             (* (second coord) scale-factor -1)
             (+
              (* 0.5 (* coords-height scale-factor ))))])
         coords)))

; graph generation and data
(defn graph-fn [x]
  (* (Math/exp (* x -1)) (Math/cos (* 2 Math/PI x)) 2))

(defonce graph-data
  (let [x-values (range 0 8 .02)]
    (normalize-to-fit
     (:width graph-dimensions)
     (:height graph-dimensions)
     (map
      vector
      x-values
      (map graph-fn x-values)))))

(defn deserialize-csv [s]
  (map
   (fn [line] (clojure.string/split line #"[,;]"))
   (clojure.string/split s #"\n")))

(def graph-stock-data (r/atom []))

(go (let [response (<! (http/get "data/AAPL.csv"))]
      ; (prn (:status response))
      (let [stock-data
            (normalize-to-fit
             (:width graph-dimensions)
             (:height graph-dimensions)
             (map vector
                  (range)
                  (map
                   (fn [row] (js/parseFloat (nth row 4)))
                   (rest
                    (deserialize-csv (:body response))))))]
        (reset! graph-stock-data stock-data))))

(def graph-heartbeat-data (r/atom []))

(go (let [response (<! (http/get "data/heartbeat.csv"))]
      ; (prn (:status response))
      (let [hb-data
            (normalize-to-fit
             (:width graph-dimensions)
             (:height graph-dimensions)
             (take 600
                   (map
                    (fn [row] [(js/parseInt (nth row 0))
                          ; boost the y axis / amplitude
                               (* 100
                                  (js/parseFloat (nth row 1)))])
                    (deserialize-csv (:body response)))))]
        (reset! graph-heartbeat-data hb-data))))

; state


(def app-params (r/atom {:epsilon 0}))


; userinterface


(def epsilon-symbol
  (.fromCharCode js/String 949)) ; = the small letter epsilon

(defn make-svg-line [coords]
  (apply str ["M"
              (clojure.string/join
               "L"
               (map
                (fn [xy] (clojure.string/join " " [(first xy) (second xy)]))
                coords))]))

(defn format-float [a]
  (when-not (nil? a)
    (gstring/format "%.3f" a)))

(defn graph-svg [data width height]
  [:svg {:width width
         :height height
          ; :style {:width "100%"}

         :viewBox (clojure.string/join " " [0 0 width height])}
   [:path {:d (make-svg-line data)
           :class "graph-line"}]
      ; points
   (map-indexed (fn [idx xy]
                  [:ellipse {:key (apply str ["elp" idx])
                             :class "graph-point"
                             :rx 2
                             :ry 2
                             :cx (first xy)
                             :cy (second xy)}])
                data)])

(defn graph-stats [data]
  [:div (gstring/format
         "%d nodes | sizeof(JSON string) = %s"
         (count data)
         (humanize-bytes
          (count (to-json-string data))))])

(defn graph-svg-with-stats [raw-data]
  (let [data (algo/douglas-peuker
              raw-data
              (:epsilon @app-params))]

    [:div
     [graph-stats data]
     [graph-svg
      data
      (:width graph-dimensions)
      (:height graph-dimensions)]]))

(defn slider [param smin smax]
  [:input {:type "range"
           :defaultValue (param @app-params)
           :max smax
           :min smin
           :step (/ (- smax smin) 150)
           ;:style {:width "100%"}
           :on-change (fn [e]
                        (.persist e)
                        ((gfunctions/debounce
                          (fn []
                            (swap! app-params assoc param (.. e -target -value)))
                          300)))}])

(defn main-ui []
  [:div
   [:div.intro
    [:h1 "Graph simplification using the Ramer-Douglas-Peuker algorithm"]
    [:p "This page is an interactive experiment to evaluate how the "
     [:a {:href "https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm"} "Ramer-Douglas-Peuker algorithm"]
     " can be used to reduce the amount of data which needs to be "
     "send to the browser in an web application to display a simple graph."]
    [:p "In the following a few datasets with different characteristica have been selected."
     " The data itself has been normalized to be in the same value range for all of the "
     "graphs to make them comparable. This is important as " epsilon-symbol " is directly affected by"
     " the range of the values."]
    [:p "The reduction in the amount of data is showed in the number of nodes "
     " as well as in the size of the data serialized into a JSON string. "
     "This example does not take other methods for size reduction like compression or reducing the precision "
     " of the serialized float values into account."]]
   [:div.controls
    [:div.slider-row
     [:span.desc epsilon-symbol]
     [slider :epsilon 0 epsilon-max]
     [:span.value
      (format-float (:epsilon @app-params))]]]

   [:div.datasets
    [:div
     [:h2 "Mathematical curve"]
     [graph-svg-with-stats graph-data]]

    [:div
     [:h2 "Stock price"]
     [:p "This is an extract from the "
      [:a {:href "https://finance.yahoo.com/quote/AAPL"} "AAPL"]
      " stock price in recent years."]
     [graph-svg-with-stats @graph-stock-data]]

    [:div
     [:h2 "Audio sampling"]
     [:p "This is a subset of a "
      [:a {:href "https://commons.wikimedia.org/wiki/File:Heartbeat.ogg"} "heartbeat recoding"]
      " sampled using this "
      [:a {:href "util/extract-heartbeat.py"} "python script"]
      "."]
     [graph-svg-with-stats @graph-heartbeat-data]]]])

(defn ^:export run []
  (r/render [main-ui]
            (js/document.getElementById "app")))