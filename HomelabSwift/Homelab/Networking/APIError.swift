import Foundation

public enum APIError: LocalizedError {
    case notConfigured
    case invalidURL
    case networkError(Error)
    case httpError(statusCode: Int, body: String)
    case decodingError(Error)
    case unauthorized
    case bothURLsFailed(primaryError: Error, fallbackError: Error)
    case custom(String)

    public var errorDescription: String? {
        let t = Translations.current()
        switch self {
        case .notConfigured:
            return t.errorNotConfigured
        case .invalidURL:
            return t.errorInvalidURL
        case .networkError(let e):
            return String(format: t.errorNetwork, e.localizedDescription)
        case .httpError(let code, let body):
            return String(format: t.errorHttp, code, body.isEmpty ? t.errorUnknown : body)
        case .decodingError(let e):
            return String(format: t.errorDecoding, e.localizedDescription)
        case .unauthorized:
            return t.errorUnauthorized
        case .bothURLsFailed:
            return t.errorBothFailed
        case .custom(let msg):
            return msg
        }
    }
}
