;;
;; Data processing of data with different filtering algorithms
;; @author Antonio Garrote
;;

(ns #^{:author "Antonio Garrote <antoniogarrote@gmail.com>"}
  clj-ml.filters
  "This namespace defines a set of functions that can be applied to data sets to modify the
   dataset in some way: transforming nominal attributes into binary attributes, removing
   attributes etc.

   There are a number of ways to use the filtering API.  The most straight forward and
   idomatic clojure way is to use the provided filter fns:

     ;; ds is the dataset
     (def ds (make-dataset :test [:a :b {:c [:g :m]}]
                                     [ [1 2 :g]
                                       [2 3 :m]
                                       [4 5 :g]]))
     (def filtered-ds
        (-> ds
            (add-attribute {:type :nominal, :column 1, :name \"pet\", :labels [\"dog\" \"cat\"]})
            (remove-attributes {:attributes [:a :c]})))


   The above functions rely on lower level fns that create and apply the filters which you may
   also use if you need more control over the actual filter objects:

     (def filter (make-filter :remove-attributes {:dataset-format ds :attributes [:a :c]}))


     ;; We apply the filter to the original data set and obtain the new one
     (def filtered-ds (filter-apply filter ds))


   The previous sample of code could be rewritten with the make-apply-filter function:

     (def filtered-ds (make-apply-filter :remove-attributes {:attributes [:a :c]} ds))"
  (:use [clj-ml utils options-utils])
  (:require [clojure [string :as str]])
  (:import (weka.filters Filter)
           (weka.core OptionHandler)
           (cljml ClojureStreamFilter ClojureBatchFilter)))


;; Options for the filters

(defmulti  #^{:skip-wiki true}
  make-filter-options
  "Creates the right parameters for a filter. Returns a clojure vector."
  (fn [kind map] kind))

(declare make-apply-filter)
;;TODO: consider passing in the make-filter-options body here as well in additon to the docstring.
(defmacro deffilter
  "Defines the filter's fn that creates a fn to make and apply the filter."
  [filter-name]
  (let [filter-keyword (keyword filter-name)]
    `(do
       (defn ~filter-name
         ([ds#]
          (make-apply-filter ~filter-keyword {} ds#))
         ([ds# attributes#]
          (make-apply-filter ~filter-keyword attributes# ds#))))))


(defmethod make-filter-options :supervised-discretize
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:invert "-V"
                          :binary "-D"
                          :better-encoding "-E"
                          :kononenko "-K"}))))

(deffilter supervised-discretize)

(defmethod make-filter-options :unsupervised-discretize
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:unset-class "-unset-class-temporarily"
                          :binary "-D"
                          :better-encoding "-E"
                          :equal-frequency "-F"
                          :optimize "-O"})
        (check-option-values m {:number-bins "-B"
                                :weight-bins "-M"}))))


(deffilter unsupervised-discretize)


(defmethod make-filter-options :pki-unsupervised-discretize
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:unset-class "-unset-class-temporarily"
                          :binary "-D"}))))


(deffilter pki-unsupervised-discretize)

(defmethod make-filter-options :supervised-nominal-to-binary
  ([kind m]
   (check-options m {:also-binary "-N" :for-each-nominal "-A"})))


(deffilter supervised-nominal-to-binary)

(defmethod make-filter-options :unsupervised-nominal-to-binary
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:invert "-V"
                          :also-binary "-N"
                          :for-each-nominal "-A"}))))

(deffilter unsupervised-nominal-to-binary)

(defmethod make-filter-options :numeric-to-nominal
  ([kind m]
   (->> (extract-attributes m) (check-options m {:invert "-V"}))))

(deffilter numeric-to-nominal)

(defmethod make-filter-options :string-to-word-vector
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:counts "-C"
                          :invert "-V"
                          :transform-tf "-T"
                          :transform-idf "-I"
                          :lowercase "-L"
                          :use-stoplist "-S"
                          :constrain-globally "-O"})
        (check-option-values m {:prefix "-P"
                                :words-to-keep "-W"
                                :prune-rate "-prune-rate"
                                :normalize "-N"
                                :stemmer "-stemmer"
                                :min-freq "-M"
                                :stopwords-file "-stopwords"
                                :tokenizer "-tokenizer"}))))

(deffilter string-to-word-vector)

(defmethod make-filter-options :normalize
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:unset-class "-unset-class-temporarily"})
        (check-option-values m {:scale "-S"
                                :translation "-T"}))))

(deffilter normalize)

(def attribute-types
  "Mapping of Weka's attribute types from clj-ml keywords to the -T flag's representation."
  {:numeric "NUM" :nominal "NOM" :string "STR" :date "DAT"})

(defmethod make-filter-options :add-attribute
  ([kind m]
   (-> m
       (update-in-when [:name] name)
       (update-in-when [:type] attribute-types)
       (update-in-when [:labels] (partial str/join ","))
       (update-in-when [:column] #(if (number? %) (inc %) %))
       (check-option-values {:type "-T"
                             :labels "-L"
                             :name "-N"
                             :column "-C"
                             :date-format "-F"}))))

(deffilter add-attribute)

(defmethod make-filter-options :reorder-attributes
  ([kind m] (extract-attributes m)))

(deffilter reorder-attributes)

(defmethod make-filter-options :remove-attributes
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:invert "-V"}))))

(deffilter remove-attributes)

(defmethod make-filter-options :remove-percentage
  ([kind m]
   (->> (check-option-values m {:percentage "-P"})
        (check-options m {:invert "-V"}))))

(deffilter remove-percentage)

(defmethod make-filter-options :remove-range
  ([kind m]
   (->> (check-option-values m {:range "-R"})
        (check-options m {:invert "-V"}))))

(deffilter remove-range)

(defmethod make-filter-options :remove-useless-attributes
  ([kind m]
   (check-option-values m {:max-variance "-M"})))

(deffilter remove-useless-attributes)

(defmethod make-filter-options :resample-unsupervised
  ([kind m]
   (->> (check-option-values m {:seed "-S" :size-percent "-Z"})
        (check-options m {:no-replacement "-no-replacement" :invert "-V"}))))

(deffilter resample-unsupervised)

(defmethod make-filter-options :resample-supervised
  ([kind m]
   (->> (check-option-values m {:seed "-S" :size-percent "-Z" :bias "-B"})
        (check-options m {:no-replacement "-no-replacement" :invert "-V"}))))

(deffilter resample-supervised)

(defmethod make-filter-options :stratified-remove-folds-supervised
  ([kind m]
   (->> (check-option-values m {:num-folds "-N" :fold "-F" :seed "-S"})
        (check-options m { :invert "-V"}))))

(deffilter stratified-remove-folds-supervised)


(defmethod make-filter-options :replace-missing-values
  ([kind m]
   (check-options m {:unset-class-temporarily "-unset-class-temporarily"})))

(deffilter replace-missing-values)

(defmethod make-filter-options :select-append-attributes
  ([kind m]
   (->> (extract-attributes m)
        (check-options m {:invert "-V"}))))

(deffilter select-append-attributes)

(defmethod make-filter-options :project-attributes
  ([kind options]
   (let [opts (if (nil? (:invert options))
                (conj options {:invert true})
                (dissoc options :invert))]
     (make-filter-options :remove-attributes opts))))

(deffilter project-attributes)

(deffilter clj-streamable)
(deffilter clj-batch)

(defmethod make-filter-options :random-subset
  ([kind m]
   (->> (check-options m {:debug "-D"})
        (check-option-values m {:num-attributes "-N"
                                :random-seed "-S"}))))

(deffilter random-subset)
;; Creation of filters

(def filter-aliases
  "Mapping of cjl-ml keywords to actual Weka classes"
  {:supervised-discretize weka.filters.supervised.attribute.Discretize
   :unsupervised-discretize weka.filters.unsupervised.attribute.Discretize
   :pki-unsupervised-discretize weka.filters.unsupervised.attribute.PKIDiscretize
   :supervised-nominal-to-binary weka.filters.supervised.attribute.NominalToBinary
   :unsupervised-nominal-to-binary weka.filters.unsupervised.attribute.NominalToBinary
   :numeric-to-nominal weka.filters.unsupervised.attribute.NumericToNominal
   :string-to-word-vector weka.filters.unsupervised.attribute.StringToWordVector
   :add-attribute weka.filters.unsupervised.attribute.Add
   :reorder-attributes weka.filters.unsupervised.attribute.Reorder
   :remove-attributes weka.filters.unsupervised.attribute.Remove
   :remove-percentage weka.filters.unsupervised.instance.RemovePercentage
   :remove-range weka.filters.unsupervised.instance.RemoveRange
   :remove-useless-attributes weka.filters.unsupervised.attribute.RemoveUseless
   :resample-unsupervised weka.filters.unsupervised.instance.Resample
   :resample-supervised weka.filters.supervised.instance.Resample
   :select-append-attributes weka.filters.unsupervised.attribute.Copy
   :replace-missing-values weka.filters.unsupervised.attribute.ReplaceMissingValues
   :project-attributes weka.filters.unsupervised.attribute.Remove
   :stratified-remove-folds-supervised weka.filters.supervised.instance.StratifiedRemoveFolds
   :random-subset weka.filters.unsupervised.attribute.RandomSubset
   :normalize weka.filters.unsupervised.attribute.Normalize})

(defn make-filter
  "Creates a filter for the provided attributes format. The first argument must be a symbol
   identifying the kind of filter to generate.
   Currently the following filters are supported:

     - :supervised-discretize
     - :unsupervised-discretize
     - :pki-unsupervised-discretize
     - :supervised-nominal-to-binary
     - :unsupervised-nominal-to-binary
     - :numeric-to-nominal
     - :string-to-word-vector
     - :add-attribute
     - :reorder-attributes
     - :remove-attributes
     - :remove-percentage
     - :remove-range
     - :remove-useless-attributes
     - :resample-unsupervised
     - :resample-supervised
     - :select-append-attributes
     - :replace-missing-values
     - :project-attributes
     - :clj-streamable
     - :clj-batch

    The second parameter is a map of attributes for the filter.
    All filters require a :dataset-format parameter:

        - :dataset-format
            The dataset where the filter is going to be applied or a
            description of the format of its attributes. Sample value:
            dataset, (dataset-format dataset)

    An example of usage:

      (make-filter :remove {:attributes [0 1] :dataset-format dataset})

    Documentation for the different filters:

    * :supervised-discretize

      An instance filter that discretizes a range of numeric attributes
      in the dataset into nominal attributes. Discretization is by Fayyad
      & Irani's MDL method (the default).

      Parameters:

        - :attributes
            Index of the attributes to be discretized, sample value: [0,4,6]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :invert
            Invert mathcing sense of the columns, sample value: true
        - :kononenko
            Use Kononenko's MDL criterion, sample value: true

    * :unsupervised-discretize

      Unsupervised version of the discretize filter. Discretization is by simple
      binning.

      Parameters:

        - :attributes
            Index of the attributes to be discretized, sample value: [0,4,6]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :unset-class
            Does not take class attribute into account for the application
            of the filter, sample-value: true
        - :binary
        - :equal-frequency
            Use equal frequency instead of equal width discretization, sample
            value: true
        - :optimize
            Optmize the number of bins using leave-one-out estimate of
            estimated entropy. Ingores the :binary attribute. sample value: true
        - :number-bins
            Defines the number of bins to divide the numeric attributes into
            sample value: 3


    * :pki-unsupervised-discretize

      Discretizes numeric attributes using equal frequency binning, where the number of bins is
      equal to the square root of the number of non-missing values.

      Parameters:

        - :attributes
            Index of the attributes to be discretized, sample value: [0,4,6]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :unset-class
            Does not take class attribute into account for the application
            of the filter, sample-value: true
        - :binary

    * :supervised-nominal-to-binary

      Converts nominal attributes into binary numeric attributes. An attribute with k values
      is transformed into k binary attributes if the class is nominal.

      Parameters:
        - :also-binary
            Sets if binary attributes are to be coded as nominal ones, sample value: true
        - :for-each-nominal
            For each nominal value one binary attribute is created, not only if the
            values of the nominal attribute are greater than two.

    * :unsupervised-nominal-to-binary

      Unsupervised version of the :nominal-to-binary filter

      Parameters:

        - :attributes
            Index of the attributes to be binarized. Sample value: [0 1 2]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :also-binary
            Sets if binary attributes are to be coded as nominal ones, sample value: true
        - :for-each-nominal
            For each nominal value one binary attribute is created, not only if the
            values of the nominal attribute are greater than two., sample value: true

    * :numeric-to-nominal

      Transforms numeric attributes into nominal ones.

      Parameters:

        - :attributes

            Index of the attributes to be transformed. Sample value: [0 1 2]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :invert
            Invert the selection of the columns. Sample value: true

    * :string-to-word-vector

      TODO

    * :add-attribute

      Adds a new attribute to the dataset. The new attribute will contain all missing values.

      Parameters:

        - :type
            Type of the new attribute. Valid options: :numeric, :nominal, :string, :date. Defaults to :numeric.
        - :name
            Name of the new attribute.
        - :column
            Index of where to insert the attribute, indexed by 0. You may also pass in \"first\" and \"last\".
            Sample values: \"first\", 0, 1, \"last\"
            The default is: \"last\"
        - :labels
            Vector of valid nominal values. This only applies when the type is :nominal.
        - :format
            The format of the date values (see ISO-8601).  This only applies when the type is :date.
            The default is: \"yyyy-MM-dd'T'HH:mm:ss\"

    * :reorder-attributes

      Reorder attributes.

      Parameters:

        - :attributes
            New ordering of the attributes. Sample value: [\"2-last\" \"1\"],
            which moves the attribute currently at position 1 to the end.
            Be sure to quote all attributes so that number indexes are not
            automatically incremented by 1 (Weka indexes start at 1).

    * :remove-attributes

      Remove some columns from the data set after the provided attributes.

      Parameters:

        - :attributes
            Index of the attributes to remove. Sample value: [0 1 2]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]

    * :remove-useless-attributes

       Remove attributes that do not vary at all or that vary too much. All constant
       attributes are deleted automatically, along with any that exceed the maximum percentage
       of variance parameter. The maximum variance test is only applied to nominal attributes.

     Parameters:

        - :max-variance
            Maximum variance percentage allowed (default 99).
            Note: percentage, not decimal. e.g. 89 not 0.89
            If you pass in a decimal Weka silently sets it to 0.0.

    * :resample-unsupervised

      \"Produces a random subsample of a dataset using either sampling
      with replacement or without replacement. The original dataset
      must fit entirely in memory. The number of instances in the
      generated dataset may be specified. When used in batch mode,
      subsequent batches are NOT resampled.\" -- from Weka JavaDoc.

      Parameters:

        - :seed
          Random number seed (integer)

        - :size-percent
          \"The size of the output dataset, as a percentage of
          the input dataset (default 100)\" (integer)

        - :no-replacement
          Use replacement or not; default is false, i.e., with replacement (boolean)

        - :invert
          Inverts the selection; can only be true if :replacement is false (boolean)

    * :resample-supervised

      \"Produces a random subsample of a dataset using either sampling
      with replacement or without replacement. The original dataset
      must fit entirely in memory. The number of instances in the
      generated dataset may be specified. The dataset must have a
      nominal class attribute. If not, use the unsupervised
      version. The filter can be made to maintain the class
      distribution in the subsample, or to bias the class distribution
      toward a uniform distribution. When used in batch mode (i.e. in
      the FilteredClassifier), subsequent batches are NOT resampled.\"
      -- from Weka JavaDoc.

      Parameters:

        - :seed
          Random number seed (integer)

        - :size-percent
          \"The size of the output dataset, as a percentage of
          the input dataset (default 100)\" (integer)

        - :bias \"Bias factor towards uniform class distribution.0 =
          distribution in input data -- 1 = uniform
          distribution. (default 0)\" (0 or 1)

        - :no-replacement
          Use replacement or not; default is false, i.e., with replacement (boolean)

        - :invert
          Inverts the selection; can only be true if :replacement is false (boolean)

    * :stratified-remove-folds-supervised

      \"This filter takes a dataset and outputs a specified fold for cross validation.
        If you do not want the folds to be stratified use the unsupervised version.\"
      -- from Weka JavaDoc

      Parameters:

        - :num-folds
          Specifies number of folds dataset is split into. (default 10)

        - :fold
          Specifies which fold is selected. (default 1)

        - :seed
          Specifies random number seed. (default 0, no randomizing)

        - :invert
          Specifies if inverse of selection is to be output.



    * :select-append-attributes

      Append a copy of the selected columns at the end of the dataset.

      Parameters:

        - :attributes
            Index of the attributes. Sample value: [1 2 3]
            The attributes may also be specified by names as well: [:some-name, \"another-name\"]
        - :invert
            Invert the selection of the columns. Sample value: true

    * :replace-missing-values

      Replaces all missing values for nominal and numeric attributes
      in a dataset with the modes and means from the training data.

      Parameters:

        - :unset-class-temporarily
            Unsets the class index temporarily before the filter is
            applied to the data. Sample value: true; default: false

    * :project-attributes

      Project some columns from the provided dataset

      Parameters:

        - :invert
            Invert the selection of columns. Sample value: true

      * :clj-streamable

      Allows you to create a custom streamable filter with clojure functions.
      A streamable filter is appropriate when you don't need to iterate over
      the entire dataset before processing it.

      Parameters:

        - :process
            This function will receive individual weka.core.Instance objects (rows
            of the dataset) and should return a newly processed Instance. The
            actual Instance is passed in and you may change it directly. However, a better
            approach is to copy the Instance with the copy method or Instance
            constructor and return a modified version of the copy.
        - :determine-dataset-format
            This function will receive the dataset's weka.core.Instances object with
            no actual Instance objects (i.e. just the format enocded in the attributes).
            You must return a Instances object that contains the new format of the
            filtered dataset.  Passing this fn is optional.  If you are not changing
            the format of the dataset then by omitting a function will use the
            current format.

      * :clj-batch

      Allows you to create a custom batch filter with clojure functions.
      A batch filter is appropriate when you need to iterate over
      the entire dataset before processing it.

      Parameters:

        - :process
            This function will receive the entire dataset as a weka.core.Instances
            objects.  A processed Instances object should be returned with the
            new Instance objects added to it.  The format of the dataset (Instances)
            that is returned from this will be returned from the filter (see below).
        - :determine-dataset-format
            This function will receive the dataset's weka.core.Instances object with
            no actual Instance objects (i.e. just the format enocded in the attributes).
            You must return a Instances object that contains the new format of the
            filtered dataset.  Passing this fn is optional.
            For many batch filters you need to process the entire dataset to determine
            the correct format (e.g. filters that operate on nominal attributes). For
            this reason the clj-batch filter will *always* use format of the dataset
            that the process fn outputs.  In other words, if you need to operate on the
            entire dataset before determining the format then this should be done in the
            process-fn and nothing needs to be passed for this fn.

   For examples on how to use the filters, especially the clojure filters, you may
   refer to filters_test.clj of clj-ml."
  [kind options]
  (capture-out-err
   (let [^Filter filter (if-let [^Class class (kind filter-aliases)]
                          (let [^OptionHandler f (.newInstance class)]
                            (.setOptions f (into-array String (make-filter-options kind options)))
                            f)
                          (case kind
                            :clj-streamable (ClojureStreamFilter. (:process options) (:determine-dataset-format options))
                            :clj-batch (ClojureBatchFilter. (:process options) (:determine-dataset-format options))))]
     (doto filter (.setInputFormat (:dataset-format options))))))

;; Processing the filtering of data

(defn filter-apply
  "Filters an input dataset using the provided filter and generates an output dataset. The
   first argument is a filter and the second parameter the data set where the filter should
   be applied."
  [filter dataset]
  (Filter/useFilter dataset filter))

(defn make-apply-filter
  "Creates a new filter with the provided options and apply it to the provided dataset.
   The :dataset-format attribute for the making of the filter will be setup to the
   dataset passed as an argument if no other value is provided.

   The application of this filter is equivalent to the consecutive application of
   make-filter and apply-filter."
  [kind options dataset]
  (let [opts (cond-> options (nil? (:dataset-format options)) (conj {:dataset-format dataset}))
        filter (make-filter kind opts)]
    (filter-apply filter dataset)))

(defn make-apply-filters
  "Creates new filters with the provided options and applies them to the provided dataset.
   The :dataset-format attribute for the making of the filter will be setup to the
   dataset passed as an argument if no other value is provided."
  [filter-options dataset]
  ;;TODO: Consider using Weka's MultiFilter instead.. could be faster for streamable filters.
  (reduce
   (fn [ds [kind options]]
     (make-apply-filter kind options ds))
   dataset
   filter-options))
