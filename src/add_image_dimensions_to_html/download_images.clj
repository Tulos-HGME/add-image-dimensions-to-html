(ns add-image-dimensions-to-html.download-images
  "Tools for downloading images from /images/ directory of HGME"
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as enlive]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NOTE: Use Clojure REPL (not Babashka REPL) for playing with this namespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def html-folder "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/")
(def downloads-folder "/Users/tobiaslocsei/Downloads/")


(defn img-tags
  "Given html as a string return a list of all the image tags in a string.
  Works even when the image tag contains newlines."
  [html-string]
  (re-seq #"(?s)<img.*?>" html-string))
(comment
  (img-tags (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/printable-thank-you-cards.html"))
  :_)


(defn img-src
  "Given an image tag as a string, return the value of the src attribute.
  Note the use of (?s) to allow match of newlines, because sometimes we have a newline
  before the closing \""
  [img-tag-string]
  (second (re-find #"(?s)src=\"(.*?)\"" img-tag-string)))


(defn img-srcs
  "Given an html file as a string, return all the srcs of all the images"
  [html-string]
  (map img-src (img-tags html-string)))
(comment
  (img-srcs (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/5-pointed-origami-star.html"))
  :_)


(defn has-open-img-tag?
  [html-str]
  (> (count (re-seq #"<img" html-str)) 0))
(comment
  (has-open-img-tag? (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/5-pointed-origami-star.html"))
  ;=> true
  (has-open-img-tag? (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/z-above-bottom-nav.shtml"))
  ;=> false
  :_)


(defn has-complete-img-tag?
  [html-str]
  (> (count (img-tags html-str)) 0))
(comment
  (has-complete-img-tag? (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/5-pointed-origami-star.html"))
  ; => true
  (has-complete-img-tag? (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/z-above-bottom-nav.shtml"))
  ; => false
  (has-complete-img-tag? (slurp "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/zz-end-of-head.shtml"))
  :_)


;; Find files that contain "<img" but don't have a complete image tag. There should be none.
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))]
    (->> file-paths
         (filter #(has-open-img-tag? (slurp %)) ,,,)
         (remove #(has-complete-img-tag? (slurp %)) ,,,)))
  ;=> ()
  :_)


;; Cross check number of images between this script vs VSCode
;; All the numbers below match results from VSCode
(comment
  ;; Complete image tags
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))]
    (count (mapcat #(img-tags (slurp %)) file-paths)))
  ;; => 9619

  ;; Partial image tags
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))]
    (count (mapcat #(re-seq #"<img" (slurp %)) file-paths)))
  ;; => 9619

  ;; Files containing image tags
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))]
    (->> file-paths
         (filter #(has-open-img-tag? (slurp %)) ,,,)
         count))
  ;; => 503
  :_)


;; Find all image tags that don't have a src. There should be none.
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        all-img-tags (mapcat #(img-tags (slurp %)) file-paths)]
    (->> all-img-tags
         (filter #(nil? (img-src %)) ,,,)
         (run! println)))
  :_)


;; Find all image tags that contain /images/
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        img-paths (mapcat #(img-srcs (slurp %)) file-paths)]
    (->> img-paths
         (filter #(str/includes? % "images/") ,,,)
         (run! println ,,,)))
  :_)


(defn hgme-images-img?
  "Check whether an image source string represents an image on the hgme site in the /images/ folder"
  [src-str]
  (or (str/starts-with? src-str "http://www.homemade-gifts-made-easy.com/images/")
      (str/starts-with? src-str "https://www.homemade-gifts-made-easy.com/images/")
      (str/starts-with? src-str "images/")))
(comment
  (hgme-images-img? "http://www.google.com/images/poweredby_transparent/poweredby_FFFFFF.gif") ; => false
  (hgme-images-img? "http://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave4.jpg") ; => true
  (hgme-images-img? "https://www.homemade-gifts-made-easy.com/images/handmade-christmas-card-ideas-glue.jpg") ; => true
  :_)


;; Find all hgme images
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        img-paths (mapcat #(img-srcs (slurp %)) file-paths)
        hgme-imgs (filter hgme-images-img? img-paths)]
    (run! println hgme-imgs)
    (println "Total:" (count hgme-imgs)))
  :_)


(defn normalize-hgme-img-url
  "Change relative url to full url, and http to https url"
  [url]
  (-> url
      (str/replace ,,, #"^images" "https://www.homemade-gifts-made-easy.com/images")
      (str/replace ,,, #"^http:" "https:")))
(comment
  (normalize-hgme-img-url "images/homemade-christmas-card-ideas-stars-3.jpg")
  ;; => "https://www.homemade-gifts-made-easy.com/images/homemade-christmas-card-ideas-stars-3.jpg"
  (normalize-hgme-img-url "http://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave7.jpg")
  ;; => "https://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave7.jpg"
  (normalize-hgme-img-url "https://www.homemade-gifts-made-easy.com/images/handmade-christmas-card-ideas-400x228.jpg")
  ;; => "https://www.homemade-gifts-made-easy.com/images/handmade-christmas-card-ideas-400x228.jpg"
  :_)


;; Check how many distinct images there are to download
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        img-paths (mapcat #(img-srcs (slurp %)) file-paths)
        hgme-imgs (filter hgme-images-img? img-paths)
        normalized-img-urls (map normalize-hgme-img-url hgme-imgs)]
    (count (distinct normalized-img-urls)))
  ;; => 604
  :_)

(defn url->filename
  [url]
  (last (str/split url #"/")))
(comment
  (url->filename "https://www.homemade-gifts-made-easy.com/images/homemade-christmas-card-ideas-stars-3.jpg")
  ;; => "homemade-christmas-card-ideas-stars-3.jpg"
  :_)


(defn download! [uri file]
 (with-open [in (io/input-stream uri)
             out (io/output-stream file)]
   (io/copy in out)))
(comment
  ;; Download "50th-birthday-gag-gifts-lollipop.jpg" to downloads folder
  ;; If there is an existing file there with the same name it will be overwritten without warning.
  (let [url "https://www.homemade-gifts-made-easy.com/images/50th-birthday-gag-gifts-lollipop.jpg"
        filename (url->filename url)]
    (download! url (str downloads-folder filename)))
  :_)


;; Next two functions are to help us check for clashing filenames
(defn distinct-images-filenames
  []
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        img-paths (mapcat #(img-srcs (slurp %)) file-paths)
        hgme-imgs (filter hgme-images-img? img-paths)
        normalized-img-urls (map normalize-hgme-img-url hgme-imgs)
        img-filenames (map url->filename normalized-img-urls)]
    (sort (distinct img-filenames))))
(comment
  (distinct-images-filenames)
  (count (distinct-images-filenames))
  :_)


(defn distinct-image-files-filenames
  []
  (let [files (fs/glob (str html-folder "image-files") "*.*")
        file-paths (sort (map str files))
        filenames (map #(last (str/split % #"/")) file-paths)]
    (sort filenames)))
(comment
  (distinct-image-files-filenames)
  (count (distinct-image-files-filenames))
  :_)


;; Check for clashing filenames
(comment
  (set/intersection (set (distinct-images-filenames))
                    (set (distinct-image-files-filenames)))
  ;; => #{"beaded-necklace-instructions-wedding.jpg"}
  ;; Identical versions in /image-files/ and /images/ so OK to overwrite
  :_)


;; Download all /images/ so we can copy them to /image-files/ locally
;; Then (manually) upload to SBI with QUI
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))
        img-paths (mapcat #(img-srcs (slurp %)) file-paths)
        hgme-imgs (filter hgme-images-img? img-paths)
        normalized-img-urls (map normalize-hgme-img-url hgme-imgs)
        url-filename-tuples (for [url normalized-img-urls] [url (url->filename url)])]
    (doseq [[url file] url-filename-tuples]
      (download! url (str downloads-folder file))))
  :_)


;; Try re-downloading all the uploaded imgs, to check that they're
;; really on SBI server under the /image-files/ subfolder
(comment
  (let [downloaded-dir
        "/Users/tobiaslocsei/Dropbox/Tobs documents/Programming/!Clojure and clojurescript/add-image-dimensions-to-html/!downloaded"
        files (fs/glob downloaded-dir "*.*")
        file-paths (sort (map str files))
        filenames (map #(last (str/split % #"/")) file-paths)
        urls (for [f filenames] (str "https://www.homemade-gifts-made-easy.com/image-files/" f))
        url-filename-tuples (map vector urls filenames)]
    (doseq [[url file] url-filename-tuples]
      (download! url (str downloads-folder file)))))
;; Successfully downloaded all 604 image files :-)




(defn update-img-url
  "If image url is old style HGME .../images/ url then update it to new style image-files/ url.
  Otherwise, return url unchanged"
  [url]
  (if (hgme-images-img? url)
    (-> (normalize-hgme-img-url url)
        (str/replace ,,, #"^https://www.homemade-gifts-made-easy.com/images" "image-files"))
    url))
(comment
  (update-img-url "https://www.foo.com/balloon.jpg")
  ;; => "https://www.foo.com/ballon.jpg"
  (update-img-url "images/homemade-christmas-card-ideas-stars-3.jpg")
  ;; => "image-files/homemade-christmas-card-ideas-stars-3.jpg"
  (update-img-url "http://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave7.jpg")
  ;; => "image-files/unique-gift-wrapping-ideas-weave7.jpg"
  (update-img-url "image-files/balloon.jpg")
  ;; => "image-files/balloon.jpg"
  :_)


(defn at-string
  "Like enlive/at, but string input and output"
  [s selector transform]
  (-> s
      (enlive/html-snippet ,,,)
      (enlive/at ,,, selector transform)
      (enlive/emit* ,,,)
      (str/join)))
(comment
  (at-string "<img src='foo.jpg'>" [:img] (enlive/do-> (enlive/set-attr :width "400")
                                                       (enlive/set-attr :height "600")))
  ;; => "<img src=\"foo.jpg\" width=\"400\" height=\"600\" />"
  (at-string "<img src='foo.jpg'>" [:img] (enlive/set-attr :src "bar.jpg"))
  ;; => "<img src=\"bar.jpg\" />"
  :_)


(comment
  (-> "<img src='foo.jpg' ***PINIT***='pin me'>"
      (enlive/html-snippet))
  :_)


(defn update-img-tag
  "If image tag's url is old style HGME .../images/ url then update it to new style image-files/ url.
  Otherwise, return tag unchanged"
  [img-tag-str]
  (let [img-src-url (img-src img-tag-str)
        updated-img-url (update-img-url img-src-url)]
    (if (= img-src-url updated-img-url)
      img-tag-str
      (let [new-tag (at-string img-tag-str [:img] (enlive/set-attr :src updated-img-url))]
        ;; Fix for enlive messing up PINIT tags
        (str/replace new-tag " pinit=" " ***PINIT***=")))))
(comment
  (update-img-tag "<img src=\"https://www.foo.com/balloon.jpg\">")
  ;; => "<img src=\"https://www.foo.com/balloon.jpg\">"
  (update-img-tag "<img src=\"images/homemade-christmas-card-ideas-stars-3.jpg\">")
  ;; => "<img src=\"image-files/homemade-christmas-card-ideas-stars-3.jpg\" />"
  (update-img-tag "<img src=\"http://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave7.jpg\" ***PINIT***=\"pin me\">")
  ;; => "<img src=\"image-files/unique-gift-wrapping-ideas-weave7.jpg\" ***PINIT***=\"pin me\" />"
  (update-img-tag "<img src=\"http://www.homemade-gifts-made-easy.com/images/unique-gift-wrapping-ideas-weave7.jpg\">")
  ;; => "<img src=\"image-files/unique-gift-wrapping-ideas-weave7.jpg\" />"
  (update-img-tag "<img src=\"image-files/unique-gift-wrapping-ideas-weave7.jpg\">")
  ;; => "<img src=\"image-files/unique-gift-wrapping-ideas-weave7.jpg\">"
  (update-img-tag "<img src=\"image-files/balloon.jpg\">")
  ;; => "<img src=\"image-files/balloon.jpg\">"
  :_)


(defn update-html-str
  "For all image tags in html page (given as a string):
  If image tag's url is old style HGME .../images/ url then update it to new style image-files/ url.
  Otherwise, return tag unchanged"
  [html-str]
  (str/replace html-str #"(?s)<img.*?>" update-img-tag))
(comment
  (println (update-html-str (slurp (str html-folder "40th-birthday-gag-gifts.html"))))
  :_)


(defn update-file!
  "Update and overwrite file in place. For all image tags in html page (given as a file path):
  If image tag's url is old style HGME .../images/ url then update it to new style image-files/ url.
  Otherwise, return tag unchanged"
  [html-file-path]
  (println "About to process" html-file-path)
  (let [html-str (slurp html-file-path)
        html-str-updated (update-html-str html-str)]
    (when-not (= html-str html-str-updated)
      (spit html-file-path html-str-updated))))
(comment
  (update-file! "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/60th-birthday-gag-gifts.html")
  (update-file! "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/dog-coloring-pages.html")
  :_)


;; DANGER ZONE
;; Update all HTML files in place
(comment
  (let [files (fs/glob html-folder "*.{shtml,html}")
        file-paths (sort (map str files))]
    (run! update-file! file-paths))
  :_)