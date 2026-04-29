package com.nextgenmanager.nextgenmanager;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordExplorerTest {
    @Test
    public void findDefaultPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$6U4n.n2O6ts0wRHkm87V6O2gCChTl7HfIpYvc5JcdopePgE68EZL.";
        String[] candidates = {"admin", "admin123", "password", "Admin@123", "Admin123", "admin@123", "Welcome@123", "P@ssword123"};
        
        System.out.println("--- PASSWORD SEARCH START ---");
        for (String c : candidates) {
            if (encoder.matches(c, hash)) {
                System.out.println("MATCH FOUND: " + c);
            }
        }
        System.out.println("--- PASSWORD SEARCH END ---");
    }
}
