package webrtsp

typealias StatusCode = Int

enum class KnownStatus(val code: StatusCode) {
    Ok(200)
}
