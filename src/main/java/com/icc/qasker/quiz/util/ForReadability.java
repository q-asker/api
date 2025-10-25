package com.icc.qasker.quiz.util;

public class ForReadability {

    public static String forReadability(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(' ');
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            sb.append(chars[i]);
            if (chars[i] == '.') {
                int nextIndex = i + 1;
                if (nextIndex < chars.length && chars[nextIndex] == ' ') {
                    i++;
                    nextIndex++;
                }
                if (nextIndex >= chars.length || chars[nextIndex] != '\n') {
                    sb.append('\n');
                }

            } else if (chars[i] == ',') {
                if (i + 1 < chars.length && chars[i + 1] != ' ') {
                    sb.append(' ');
                }
            }
        }
        return sb.toString();
    }
}
