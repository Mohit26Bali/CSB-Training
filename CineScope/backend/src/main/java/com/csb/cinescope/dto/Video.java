package com.csb.cinescope.dto;

/**
 * A trailer or teaser hosted on YouTube. {@code key} is the YouTube video id; the frontend plays it in an embedded
 * player (youtube-nocookie.com). Only YouTube videos are kept.
 */
public record Video(String key, String name, String type, boolean official) {
}
