package com.terraboxstudios.nanopay.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SecureRandomUtil {

    public static SecureRandom getSecureRandom() throws NoSuchAlgorithmException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("nix") || os.contains("nux")) {
            try {
                return SecureRandom.getInstance("NativePRNGNonBlocking");
            } catch (NoSuchAlgorithmException e) {
                try {
                    return SecureRandom.getInstance("NativePRNG");
                } catch (NoSuchAlgorithmException ex) {
                    return SecureRandom.getInstanceStrong();
                }
            }
        }
        return SecureRandom.getInstanceStrong();
    }

}
