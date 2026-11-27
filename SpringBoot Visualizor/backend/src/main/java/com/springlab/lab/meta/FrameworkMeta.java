package com.springlab.lab.meta;

import java.util.Map;

/**
 * Runtime facts shown as the lessons' assumptions. Built from an explicit allow-list; nothing here is derived from
 * secrets, credentials, connection strings, file paths or user data.
 */
public record FrameworkMeta(Map<String, String> versions, Map<String, String> settings) {
}
