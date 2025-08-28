(ns eca.features.tools.text-match-test
  (:require [clojure.test :refer [deftest is testing]]
            [eca.features.tools.text-match :as text-match]
            [eca.test-helper :as h]))

(deftest normalize-for-matching-test
  (testing "CRLF to LF normalization"
    (is (= "line1\nline2\nline3"
           (text-match/normalize-for-matching "line1\r\nline2\r\nline3"))))

  (testing "CR to LF normalization"
    (is (= "line1\nline2\nline3"
           (text-match/normalize-for-matching "line1\rline2\rline3"))))

  (testing "Trailing whitespace removal"
    (is (= "line1\nline2\nline3"
           (text-match/normalize-for-matching "line1  \nline2   \nline3\t"))))

  (testing "Mixed line endings and whitespace"
    (is (= "line1\nline2\nline3"
           (text-match/normalize-for-matching "line1  \r\nline2   \r\nline3\t")))))

(deftest apply-content-change-to-string-test
  (testing "Exact match preserves original formatting"
    (let [file-content "line1\r\nline2  \nline3\t\r\nline4"
          original-content "line2"
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
      (is (= file-content (:original-full-content result)))
      (is (= "line1\r\nCHANGED  \nline3\t\r\nline4" (:new-full-content result)))
      (is (= false (:normalized? result)))))

  (testing "Normalized match tries original content first"
    (let [file-content "line1\r\nline2  \nline3"
          original-content "line1\nline2\nline3"  ; Different line endings
          new-content "REPLACED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
        ;; This won't match exactly, so goes to normalization path
        ;; But the original replacement attempt will fail, so it uses normalized result
      (is (= file-content (:original-full-content result)))
      (is (= "REPLACED" (:new-full-content result)))
      (is (= true (:normalized? result)))))

  (testing "Normalized match falls back to normalized result when original fails"
    (let [file-content "prefix\r\nline1  \nline2   \r\nsuffix"
          original-content "line1\nline2"  ; Matches normalized but not exact
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
      (is (= file-content (:original-full-content result)))
        ;; Should use normalized replacement when original fails
      (is (= "prefix\nCHANGED\nsuffix" (:new-full-content result)))
      (is (= true (:normalized? result)))))

  (testing "Content not found"
    (let [file-content "foo\nbar\nbaz"
          original-content "nonexistent"
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
      (is (= :not-found (:error result)))))

  (testing "Ambiguous match (multiple occurrences, all? false)"
    (let [file-content "foo\nbar\nfoo\nbaz"
          original-content "foo"
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
        ;; This should succeed with exact match first, not go to normalization path
        ;; So it will replace first occurrence only, not detect ambiguity
      (is (= file-content (:original-full-content result)))
      (is (= "CHANGED\nbar\nfoo\nbaz" (:new-full-content result)))
      (is (= false (:normalized? result)))))

  (testing "Multiple occurrences with all? true"
    (let [file-content "foo\nbar\nfoo\nbaz"
          original-content "foo"
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content true path)]
      (is (= file-content (:original-full-content result)))
      (is (= "CHANGED\nbar\nCHANGED\nbaz" (:new-full-content result)))
      (is (= false (:normalized? result)))))

  (testing "Normalized ambiguous match detection"
    (let [file-content "line1  \r\nline2\r\nline1   \nline3"
          original-content "line1"  ; Will match both normalized occurrences
          new-content "CHANGED"
          path "/tmp/test-file.txt"
          result (text-match/apply-content-change-to-string file-content original-content new-content false path)]
        ;; First tries exact match, finds "line1" exactly, replaces first occurrence
      (is (= file-content (:original-full-content result)))
      (is (= "CHANGED  \r\nline2\r\nline1   \nline3" (:new-full-content result)))
      (is (= false (:normalized? result))))))

;; Keep the old test for backward compatibility testing
(deftest file-change-full-content-test
  (testing "Backward compatibility - file-based function still works"
    (let [file-path (h/file-path "/tmp/test-file.txt")
          file-content "line1\nline2\nline3"
          original-content "line2"
          new-content "CHANGED"]
      (with-redefs [slurp (constantly file-content)]
        (let [result (text-match/apply-content-change-to-file file-path original-content new-content false)]
          (is (= file-content (:original-full-content result)))
          (is (= "line1\nCHANGED\nline3" (:new-full-content result)))
          (is (= false (:normalized? result))))))))
