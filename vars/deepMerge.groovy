// Nested maps merger
def call(Map defaults, Map overrides) {
    def result = [:] + defaults
    overrides.each { k, v ->
        if (result[k] instanceof Map && v instanceof Map) {
            result[k] = deepMerge(result[k] as Map, v as Map)
        } else {
            result[k] = v
        }
    }
    return result
}
