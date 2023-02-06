#!/usr/bin/env bb
(ns add-image-dimensions-to-html.add-images-dimensions-to-html
  (:require [babashka.pods :as pods]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))
(pods/load-pod "bootleg")
(require '[pod.retrogradeorbit.bootleg.enlive :as enlive])


(def hgme-root (str (System/getProperty "user.home") "/Dropbox/Shared SBI/HGME UYOH/"))


(def example-html (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/dog-coloring-pages.html"))


(def img-tag-re #"(?s)<img.*?>")


(defn img-tags
  "Given html as a string return a list of all the image tags in a string.
  Works even when the image tag contains newlines."
  [html-string]
  (re-seq img-tag-re html-string))
(comment
  (img-tags "blah blah <img src=\"foo.jpg\"> blah <img src=\"bar.jpg\"> blah")
  ; => ("<img src=\"foo.jpg\">" "<img src=\"bar.jpg\">")
  (count (img-tags example-html))
  (count (distinct (img-tags example-html)))
  (println (first (img-tags example-html)))
  (run! println (img-tags (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/birthday-wishes.html")))
  :_)


(defn img-src
  "Given an image tag as a string, return the value of the src attribute.
  Note the use of (?s) to allow match of newlines, because sometimes we have a newline
  before the closing \""
  [img-tag-string]
  (second (re-find #"(?s)src=\"(.*?)\"" img-tag-string)))
(comment
  (img-src (first (img-tags "blah blah <img src=\"foo.jpg\"> blah <img src=\"bar.jpg\"> blah")))
  ; => "foo.jpg"
  :_)


(defn set-dimensions
  "Given an image tag as a string, set the width and height attributes, while
  making sure to preserve the capitalisation of the ***PINIT*** tag.
  img-tag        - str
  width          - int or str
  height         - int or str"
  [{:keys [img-tag width height]}]
  (let [new-img-tag
  (-> img-tag
      (enlive/at ,,, [:img]
                 (enlive/do->
                   (enlive/set-attr :width (str width))
                   (enlive/set-attr :height (str height))))
      ;; Enlive makes pinit lower case so we need to convert it back to uppercase
            (str/replace ,,, "***pinit***" "***PINIT***"))]
    ;; Close the img tag the same way as the original tag, i.e. > or />. Otherwise enlive
    ;; always changes the closing tag to plain >
    (if (str/ends-with? img-tag "/>")
      (str/replace new-img-tag ">" "/>")
      new-img-tag)))
(comment
  (set-dimensions {:img-tag "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\">"
                   :width 300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"

  ;; Check that it works when the tag closes with /> instead of >
  (set-dimensions {:img-tag "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\"/>"
                   :width 300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"
  :_)


(defn dimensions
  "Given path of image file as str, return vector [w h] of dimensions.
  Return nil if dimensions cannot be calculated (e.g. if file not found)"
  [path-str]
  (let [sips-result (shell/sh "sips"
                              "-g" "pixelWidth" "-g" "pixelHeight"
                              path-str)]
    (if-not (= 0 (:exit sips-result))
      (do
        ;; Print warning and return nil if there's an error computing image dimensions
        (println (:err sips-result))
        nil)
      (let [[width-line height-line] (->> (:out sips-result)
                                          (str/split-lines ,,,)
                                          (drop 1 ,,,))
            width (->> (str/split width-line #": ")
                       (second ,,,)
                       (edn/read-string ,,,))
            height (->> (str/split height-line #": ")
                        (second ,,,)
                        (edn/read-string ,,,))]
        [width height]))))
(comment
  (dimensions (str hgme-root "image-files/fathers-day-coloring-pages-400x352.png"))
  ; => [400 352]
  (dimensions (str hgme-root "this-path-does-not-exist")) ; => nil
  (dimensions "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/image-files/40th-birthday-ideas-gifts-for-women-600x800.jpg")
  :_)


(defn update-img-tag
  "Given an image tag string, return image tag str with added  height and width attributes.
  The height and width are found by looking up the image on the local disk"
  [img-tag-str]
  (let [src (img-src img-tag-str)]
    (if-not (str/starts-with? src "image-files/") ;;
      ;; Return image tag unchanged if it refers to a non-HGME image
      img-tag-str
      (if-let [[w h] (dimensions (str hgme-root src))]
        (set-dimensions {:img-tag img-tag-str
                         :width w
                         :height h})
        ;; Return img-tag-str unchanged if dimensions could not be found
        img-tag-str))))
(comment
  (update-img-tag "<img ***PINIT***=\"235 Happy Birthday Wishes &amp; Quotes\" alt=\"birthday wishes\" src=\"image-files/birthday-wishes-for-friend-its-time-to-shine-1080x720.png\"/>")
  ; => "<img ***PINIT***=\"235 Happy Birthday Wishes &amp; Quotes\" alt=\"birthday wishes\" src=\"image-files/birthday-wishes-for-friend-its-time-to-shine-1080x720.png\" width=\"1080\" height=\"721\">"

  (update-img-tag "<img src=\"image-files/i-do-not-exist.jpg\">")
  ; => "<img src=\"image-files/i-do-not-exist.jpg\">"  (and warning printed to stdout)

  (update-img-tag "<img src=\"images/i-am-not-in-the-right-folder.jpg\">")
  ; => "<img src=\"images/i-am-not-in-the-right-folder.jpg\">"

  (update-img-tag "<img src=\"www.another-domain.com/image.jpg\">")
  ; => "<img src=\"www.another-domain.com/image.jpg\">"

  (update-img-tag (last (img-tags (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/birthday-wishes.html"))))
  :_)


(defn update-html
  "Given an html string, update all image tags to include width and height attributes
  and return the new string.
  The height and width are found by looking up the image on the local disk"
  [html-str]
  (str/replace html-str img-tag-re update-img-tag))
(comment
  (println (update-html example-html))
  (println (update-html (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/birthday-wishes.html")))
  :_)


(defn update-file!
  "Given the path to an html file, overwrite it in place with a version
  where image tags are updated to include with and height attributes.
  The height and width are found by looking up the image the local disk."
  [html-file-path]
  (println "Checking" html-file-path)
  (let [html-str (slurp html-file-path)
        updated-html-str (update-html html-str)]
    (if (= html-str updated-html-str)
      (println "No changes made\n")
      (do
        (spit html-file-path updated-html-str)
        (println "File updated\n")))))
(comment
  (let [file-path (str hgme-root "cat-coloring-pages.html")]
    (update-file! file-path))

  (let [file-path (str hgme-root "privacy-policy.html")]
    (update-file! file-path))
  :_)


(defn -main
  []
  (let [files *command-line-args*]
    (doseq [f files]
      (update-file! f))))


;; Run -main if invoked from the command line. See https://book.babashka.org/#main_file
(when (= *file* (System/getProperty "babashka.file"))
  (-main))


;; Copy script to /bin folder
(comment
  (shell/sh
    "cp"
    (str (System/getProperty "user.dir") "/src/add_image_dimensions_to_html/add_images_dimensions_to_html.clj")
    "/Users/tobiaslocsei/Dropbox/Tobs documents/Programming/bin")
  :_)


