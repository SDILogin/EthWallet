import CryptoKit
import Foundation
import Security

// C ABI called only by MasterKekProtectionSecureEnclave through JNA. Kotlin validates the buffer sizes.

private let statusOk: Int32 = 0
private let statusSecureEnclaveUnavailable: Int32 = 1
private let statusFailure: Int32 = 2
private let statusOutputTooSmall: Int32 = 3

private let symmetricKeyLength = 32
private let ephemeralPublicKeyLength = 65
private let kdfSharedInfo = Data("EthWallet master KEK v2".utf8)

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
                [.privateKeyUsage],
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

/// Writes `ephemeralPublicKey (65) || nonce (12) || ciphertext (payloadLength) || tag (16)` to `output`.
@_cdecl("ethwalletSecureEnclaveWrap")
public func ethwalletSecureEnclaveWrap(
    _ key: UnsafePointer<UInt8>,
    _ keyLength: Int32,
    _ payload: UnsafePointer<UInt8>,
    _ payloadLength: Int32,
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
            let sealedBox = try AES.GCM.seal(Data(bytes: payload, count: Int(payloadLength)), using: symmetricKey)
            let wrapped = ephemeralKey.publicKey.x963Representation + sealedBox.combined!
            wrapped.copyBytes(to: output, count: wrapped.count)
            return statusOk
        } catch {
            return failure(error)
        }
    }
}

/// Reverses `ethwalletSecureEnclaveWrap`; writes `wrappedLength - 93` bytes to `output`.
@_cdecl("ethwalletSecureEnclaveUnwrap")
public func ethwalletSecureEnclaveUnwrap(
    _ key: UnsafePointer<UInt8>,
    _ keyLength: Int32,
    _ wrapped: UnsafePointer<UInt8>,
    _ wrappedLength: Int32,
    _ output: UnsafeMutablePointer<UInt8>
) -> Int32 {
    autoreleasepool { () -> Int32 in
        do {
            let privateKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
                dataRepresentation: Data(bytes: key, count: Int(keyLength))
            )
            let wrappedData = Data(bytes: wrapped, count: Int(wrappedLength))
            let ephemeralPublicKey = try P256.KeyAgreement.PublicKey(
                x963Representation: wrappedData.prefix(ephemeralPublicKeyLength)
            )
            let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)
            let symmetricKey = deriveSymmetricKey(
                sharedSecret: sharedSecret,
                ephemeralPublicKey: ephemeralPublicKey,
                recipientPublicKey: privateKey.publicKey
            )
            let sealedBox = try AES.GCM.SealedBox(combined: wrappedData.dropFirst(ephemeralPublicKeyLength))
            let payload = try AES.GCM.open(sealedBox, using: symmetricKey)
            payload.copyBytes(to: output, count: payload.count)
            return statusOk
        } catch {
            return failure(error)
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
