package com.example

import okhttp3.CertificatePinner
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testCertificatePinning() {
    val host = "nano-money.yasinhacker135.workers.dev"
    val pinner = CertificatePinner.Builder()
        .add(host, "sha256/C5Ia9L779Af7sKT76T3GfXz3N68A3v4M8158gX768w==")
        .add(host, "sha256/iie7gV1YqiM2ODUYFBcRY1SBA1EPpkK0gTxF9IPKgpM=")
        .add(host, "sha256/r390S9n7Q62yEkt4N2Gg08VzX1v6K7Yv0v682C7aXjA=")
        .add(host, "sha256/k2VNoEB9-Ip8vID1Ea9d18FEXI7P_Fk_m9_R18F083k=")
        .add(host, "sha256/IqgdnTe45+B6KZ+fWXc8pOjOuE9/56+ArZAaDhOhVXI=")
        .add(host, "sha256/kldp6NNEd8wsugYyyIYFsi1yIMCED3hZbSR8Zfsa/A4=")
        .add(host, "sha256/mEfIZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        .build()

    println("Pins for $host: " + pinner.findMatchingPins(host))
    
    for (pin in pinner.findMatchingPins(host)) {
        println("Pin pattern: ${pin.pattern}, hash: ${pin.hash.base64()}")
    }
  }
}
