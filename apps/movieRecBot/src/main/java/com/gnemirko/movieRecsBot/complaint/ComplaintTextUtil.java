package com.gnemirko.movieRecsBot.complaint;

import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;

final class ComplaintTextUtil {

    private ComplaintTextUtil() {
    }

    static String describeUser(User user) {
        if (user == null) {
            return "unknown";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(user.getFirstName())) {
            parts.add(user.getFirstName().trim());
        }
        if (StringUtils.hasText(user.getLastName())) {
            parts.add(user.getLastName().trim());
        }
        String fullName = String.join(" ", parts);
        String username = user.getUserName();

        if (StringUtils.hasText(fullName) && StringUtils.hasText(username)) {
            return fullName + " (@" + username + ", id=" + user.getId() + ")";
        }
        if (StringUtils.hasText(fullName)) {
            return fullName + " (id=" + user.getId() + ")";
        }
        if (StringUtils.hasText(username)) {
            return "@" + username + " (id=" + user.getId() + ")";
        }
        return "id=" + user.getId();
    }
}
