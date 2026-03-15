package adapter.converter

interface Converter<ORIGINAL_BODY_TYPE, ADAPTED_BODY_TYPE> {
    fun adapt(original: ORIGINAL_BODY_TYPE): ADAPTED_BODY_TYPE
}
