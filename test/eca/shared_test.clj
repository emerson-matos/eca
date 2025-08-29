(ns eca.shared-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.shared :as shared]
   [eca.test-helper :as h]))

(deftest uri->filename-test
  (testing "should decode special characters in file URI"
    (is (= (h/file-path "/path+/encoded characters!")
           (shared/uri->filename (h/file-uri "file:///path%2B/encoded%20characters%21")))))
  (testing "Windows URIs"
    (is (= (when h/windows? "C:\\c.clj")
           (when h/windows? (shared/uri->filename "file:/c:/c.clj"))))
    (is (= (when h/windows? "C:\\Users\\FirstName LastName\\c.clj")
           (when h/windows? (shared/uri->filename "file:/c:/Users/FirstName%20LastName/c.clj"))))
    (is (= (when h/windows? "C:\\c.clj")
           (when h/windows? (shared/uri->filename "file:///c:/c.clj"))))))

(deftest assoc-some-test
  (testing "single association"
    (is (= {:a 1} (shared/assoc-some {} :a 1)))
    (is (= {} (shared/assoc-some {} :a nil))))
  (testing "multiple associations"
    (is (= {:a 1 :b 2}
           (shared/assoc-some {} :a 1 :b 2)))
    (is (= {:a 1}
           (shared/assoc-some {} :a 1 :b nil)))
    (is (= {}
           (shared/assoc-some {} :a nil :b nil))))
  (testing "throws on uneven kvs"
    (is (thrown? IllegalArgumentException
                 (shared/assoc-some {} :a 1 :b)))))

(deftest tokens->cost-test
  (let [model-capabilities {:input-token-cost 0.01
                            :output-token-cost 0.02
                            :input-cache-creation-token-cost 0.005
                            :input-cache-read-token-cost 0.001}]
    (testing "basic input/output cost"
      (is (= "0.70" (shared/tokens->cost 30 nil nil 20 model-capabilities))))
    (testing "with cache creation tokens"
      (is (= "0.75" (shared/tokens->cost 30 10 nil 20 model-capabilities))))
    (testing "with cache read tokens"
      (is (= "0.73" (shared/tokens->cost 30 nil 30 20 model-capabilities))))
    (testing "with both cache creation and read tokens"
      (is (= "0.78" (shared/tokens->cost 30 10 30 20 model-capabilities))))
    (testing "returns nil when model is missing from db"
      (is (nil? (shared/tokens->cost 30 nil nil 20 {}))))
    (testing "returns nil when mandatory costs are missing"
      (is (nil? (shared/tokens->cost 30 nil nil 20 {:input-token-cost 0.01}))))))
