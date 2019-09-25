(ns ^:figwheel-always graphsimplification.algo)

; The implementation here is certainly not optimal, but
; should be sufficient for this test case;
;
; More implementations:
; https://rosettacode.org/wiki/Ramer-Douglas-Peucker_line_simplification

(defn pependicular-distance [coord coord-start coord-end]
  (let
   [[dx dy] (let
    ; normalize
             [dx (- (first coord-end) (first coord-start))
              dy (- (second coord-end) (second coord-start))
              mag (Math/hypot dx dy)]
              (if (> mag 0.0)
                [(/ dx mag) (/ dy mag)]
                [dx dy]))
    pvx (- (first coord) (first coord-start))
    pvy (- (second coord) (second coord-start))

    ; Get dot product (project pv onto normalized direction)
    pvdot (+ (* dx pvx) (* dy pvy))]
    ; Scale line direction vector and subtract it from pv
    (Math/hypot (- pvx (* pvdot dx)) (- pvy (* pvdot dy)))))

(defn max-point-distance [coords]
  (loop [c-index 0
         max-index 0
         end (count coords)
         max-d 0]
    (if (= c-index end)
      [max-index max-d]
      (let [d (pependicular-distance
               (nth coords c-index)
               (first coords)
               (last coords))]
        (if (nil? max-index)
          (recur (inc c-index) c-index end d)
          (if (> d max-d)
            (recur (inc c-index) c-index end d)
            (recur (inc c-index) max-index end max-d)))))))

(defn douglas-peuker [coords epsilon]
  ; TODO
  (if (and (> epsilon 0.0) (not (empty? coords)))
    (let [[max-index max-d] (max-point-distance coords)]
      (if (nil? max-index)
        []
        (if (> max-d epsilon)
          (concat
           (douglas-peuker (take
                            (max 0 (- max-index 1))
                            coords)
                           epsilon)
           (douglas-peuker (drop max-index coords) epsilon))
          [(first coords) (last coords)])))
    coords))