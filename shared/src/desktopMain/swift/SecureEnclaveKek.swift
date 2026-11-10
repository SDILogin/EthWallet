import CryptoKit
import CryptoTokenKit
import Foundation
import LocalAuthentication
import Security

// C ABI called only by MasterKekProtectionSecureEnclave through JNA. Kotlin validates the fixed buffer sizes.

private let statusOk: Int32 = 0
private let statusSecureEnclaveUnavailable: Int32 = 1
private let statusFailure: Int32 = 2
private let statusOutputTooSmall: Int32 = 3
private let statusUserCancelled: Int32 = 4

private let masterKekLength = 32
private let symmetricKeyLength = 32
private let ephemeralPublicKeyLength = 65
private let wrappedMasterKekLength = 125
private let kdfSharedInfo = Data("EthWallet master KEK v1".utf8)
private let unlockReason = "unlock your EthWallet wallets"
private let cancelledAuthenticationErrors: [(domain: String, code: Int)] = [
    (LAErrorDomain, LAError.Code.userCancel.rawValue),
    (LAErrorDomain, LAError.Code.appCancel.rawValue),
    (LAErrorDomain, LAError.Code.systemCancel.rawValue),
    (TKErrorDomain, TKError.Code.canceledByUser.rawValue),
    (NSOSStatusErrorDomain, Int(errSecUserCanceled)),
]

@_cdecl("ethwalletSecureEnclaveCreateKey")
public func ethwalletSecureEnclaveCreateKey(
    _ output: UnsafeMutablePointer<UInt8>,
    _ outputCapacity: Int32,
    _ outputLength: UnsafeMutablePointer<Int32>
) -> Int32 {
    guard SecureEnclave.isAvailable else { return statusSecureEnclaveUnavailable }
    return autoreleasepool { () -> Int32 in
        var error: Unmanaged<CFError>?
        guard
            let accessControl = SecAccessControlCreateWithFlags(
                kCFAllocatorDefault,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                [.privateKeyUsage, .userPresence],
                &error
            )
        else {
            return failure(error!.takeRetainedValue())
        }
        do {
            let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(accessControl: accessControl)
            let keyData = key.dataRepresentation
            guard keyData.count <= Int(outputCapacity) else { return statusOutputTooSmall }
            keyData.copyBytes(to: output, count: keyData.count)
            outputLength.pointee = Int32(keyData.count)
            return statusOk
        } catch {
            return failure(error)
        }
    }
}

/// Writes `ephemeralPublicKey (65) || nonce (12) || ciphertext (32) || tag (16)` to `output`. Uses only the public
/// key, so it never asks for user presence.
@_cdecl("ethwalletSecureEnclaveWrap")
public func ethwalletSecureEnclaveWrap(
    _ key: UnsafePointer<UInt8>,
    _ keyLength: Int32,
    _ masterKek: UnsafePointer<UInt8>,
    _ output: UnsafeMutablePointer<UInt8>
) -> Int32 {
    autoreleasepool { () -> Int32 in
        do {
            let privateKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
                dataRepresentation: Data(bytes: key, count: Int(keyLength))
            )
            let recipientPublicKey = privateKey.publicKey
            let ephemeralKey = P256.KeyAgreement.PrivateKey()
            let sharedSecret = try ephemeralKey.sharedSecretFromKeyAgreement(with: recipientPublicKey)
            let symmetricKey = deriveSymmetricKey(
                sharedSecret: sharedSecret,
                ephemeralPublicKey: ephemeralKey.publicKey,
                recipientPublicKey: recipientPublicKey
            )
            let sealedBox = try AES.GCM.seal(Data(bytes: masterKek, count: masterKekLength), using: symmetricKey)
            let wrapped = ephemeralKey.publicKey.x963Representation + sealedBox.combined!
            wrapped.copyBytes(to: output, count: wrappedMasterKekLength)
            return statusOk
        } catch {
            return failure(error)
        }
    }
}

/// Shows the Touch ID or macOS password sheet and blocks until the user answers it.
@_cdecl("ethwalletSecureEnclaveUnwrap")
public func ethwalletSecureEnclaveUnwrap(
    _ key: UnsafePointer<UInt8>,
    _ keyLength: Int32,
    _ wrappedMasterKek: UnsafePointer<UInt8>,
    _ output: UnsafeMutablePointer<UInt8>
) -> Int32 {
    autoreleasepool { () -> Int32 in
        do {
            let context = LAContext()
            context.localizedReason = unlockReason
            let privateKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
                dataRepresentation: Data(bytes: key, count: Int(keyLength)),
                authenticationContext: context
            )
            let wrapped = Data(bytes: wrappedMasterKek, count: wrappedMasterKekLength)
            let ephemeralPublicKey = try P256.KeyAgreement.PublicKey(
                x963Representation: wrapped.prefix(ephemeralPublicKeyLength)
            )
            let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)
            let symmetricKey = deriveSymmetricKey(
                sharedSecret: sharedSecret,
                ephemeralPublicKey: ephemeralPublicKey,
                recipientPublicKey: privateKey.publicKey
            )
            let sealedBox = try AES.GCM.SealedBox(combined: wrapped.dropFirst(ephemeralPublicKeyLength))
            let masterKek = try AES.GCM.open(sealedBox, using: symmetricKey)
            masterKek.copyBytes(to: output, count: masterKekLength)
            return statusOk
        } catch {
            return isCancelledAuthentication(error) ? statusUserCancelled : failure(error)
        }
    }
}

private func deriveSymmetricKey(
    sharedSecret: SharedSecret,
    ephemeralPublicKey: P256.KeyAgreement.PublicKey,
    recipientPublicKey: P256.KeyAgreement.PublicKey
) -> SymmetricKey {
    sharedSecret.hkdfDerivedSymmetricKey(
        using: SHA256.self,
        salt: ephemeralPublicKey.x963Representation + recipientPublicKey.x963Representation,
        sharedInfo: kdfSharedInfo,
        outputByteCount: symmetricKeyLength
    )
}

private func failure(_ error: Error) -> Int32 {
    NSLog("EthWallet Secure Enclave operation failed: %@", error as NSError)
    return statusFailure
}

/// CryptoKit may report a dismissed sheet as a Security or CryptoTokenKit error wrapping the LocalAuthentication one.
private func isCancelledAuthentication(_ error: Error) -> Bool {
    var current: NSError? = error as NSError
    while let nsError = current {
        let cancelled = cancelledAuthenticationErrors.contains { cancelledError in
            cancelledError.domain == nsError.domain && cancelledError.code == nsError.code
        }
        if cancelled {
            return true
        } else {
            current = nsError.userInfo[NSUnderlyingErrorKey] as? NSError
        }
    }
    return false
}
