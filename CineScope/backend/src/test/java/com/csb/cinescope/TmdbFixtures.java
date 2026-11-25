package com.csb.cinescope;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Canned TMDB answers (shaped exactly like the real API) shared by the backend tests, so no test needs the network or
 * a real token. Used by {@code mapper/TitleMapperTest}, {@code service/CatalogServiceTest},
 * {@code tmdb/TmdbHttpGatewayTest} and {@code controller/CatalogApiTest}.
 *
 * <p>The data is deliberately awkward in the ways the real data is: missing images, empty release dates, entries that
 * are neither movie nor show, Vimeo and clip videos mixed in with YouTube trailers, a certification list whose first
 * entry is blank.
 */
public final class TmdbFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TmdbFixtures() {
    }

    public static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    /** /movie/550/watch/providers: India with every group (a duplicate and a service in two groups on purpose), US with one. */
    public static final String PROVIDERS = """
            {"id":550,"results":{
              "IN":{"link":"https://www.themoviedb.org/movie/550/watch?locale=IN",
                "flatrate":[
                  {"provider_id":122,"provider_name":"JioHotstar","logo_path":"/hs.jpg","display_priority":5},
                  {"provider_id":8,"provider_name":"Netflix","logo_path":"/nf.jpg","display_priority":1},
                  {"provider_id":8,"provider_name":"Netflix","logo_path":"/nf.jpg","display_priority":1}],
                "ads":[{"provider_id":300,"provider_name":"Pluto","logo_path":null,"display_priority":2}],
                "free":[{"provider_id":301,"provider_name":"MX Player","logo_path":"/mx.jpg","display_priority":1},
                        {"provider_id":300,"provider_name":"Pluto","logo_path":null,"display_priority":2}],
                "rent":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/atv.jpg","display_priority":3}],
                "buy":[{"provider_id":3,"provider_name":"Google Play Movies","logo_path":"/gp.jpg","display_priority":4}]},
              "US":{"link":"https://www.themoviedb.org/movie/550/watch?locale=US","buy":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/atv.jpg","display_priority":1}]}}}""";

    /**
     * /person/3001 with combined_credits. On purpose: a talk-show appearance and a "person" entry (both must be dropped),
     * an undated film, a title credited as both actor and in the crew, and a crew member with two jobs on one film.
     */
    public static final String PERSON = """
            {"id":3001,"name":"Mara Whitfield","biography":"Born in a lighthouse.","birthday":"1975-04-12","deathday":null,
             "place_of_birth":"Leeds, England","known_for_department":"Acting","profile_path":"/mw.jpg","homepage":"",
             "combined_credits":{
               "cast":[
                 {"id":1,"media_type":"movie","title":"Old Film","release_date":"1999-05-01","poster_path":"/old.jpg","vote_average":7.1,"vote_count":10,"character":"Anna","genre_ids":[18]},
                 {"id":2,"media_type":"tv","name":"Long Show","first_air_date":"2010-01-01","poster_path":"/ls.jpg","vote_average":8.0,"vote_count":90,"character":"Dr Vale","genre_ids":[18]},
                 {"id":3,"media_type":"tv","name":"Late Night Chat","first_air_date":"2015-01-01","poster_path":null,"vote_count":500,"character":"Self","genre_ids":[10767]},
                 {"id":4,"media_type":"movie","title":"Unreleased","release_date":"","poster_path":"/un.jpg","vote_count":5,"character":null,"genre_ids":[]},
                 {"id":5,"media_type":"person","name":"Not a title"},
                 {"id":6,"media_type":"movie","title":"New Film","release_date":"2022-03-03","poster_path":"/new.jpg","vote_average":6.5,"vote_count":50,"character":"Eve","genre_ids":[]},
                 {"id":8,"media_type":"tv","name":"Famous Sitcom","first_air_date":"2001-09-01","poster_path":"/fs.jpg","vote_count":9000,"character":"Guest","episode_count":1,"order":3,"genre_ids":[]},
                 {"id":9,"media_type":"movie","title":"Minor Part","release_date":"2003-01-01","poster_path":"/mp.jpg","vote_count":8000,"character":"Waiter","order":40,"genre_ids":[]}],
               "crew":[
                 {"id":6,"media_type":"movie","title":"New Film","release_date":"2022-03-03","poster_path":"/new.jpg","vote_count":50,"job":"Director","genre_ids":[]},
                 {"id":6,"media_type":"movie","title":"New Film","release_date":"2022-03-03","poster_path":"/new.jpg","vote_count":50,"job":"Writer","genre_ids":[]},
                 {"id":7,"media_type":"movie","title":"Behind Scenes","release_date":"2005-01-01","poster_path":"/bs.jpg","vote_count":20,"job":"Producer","genre_ids":[]}]}}""";

    /** /trending/all/week: a movie, a show, a person (must be ignored) and a movie without any images or date. */
    public static final String TRENDING_ALL = """
            {"page":1,"total_pages":3,"total_results":60,"results":[
              {"id":101,"media_type":"movie","title":"The Last Lighthouse","overview":"A keeper guards a secret.",
               "poster_path":"/p101.jpg","backdrop_path":"/b101.jpg","release_date":"2026-09-12","vote_average":7.8},
              {"id":202,"media_type":"tv","name":"Harbor Lights","overview":"Small town, big storms.",
               "poster_path":"/p202.jpg","backdrop_path":"/b202.jpg","first_air_date":"2025-03-01","vote_average":8.1},
              {"id":303,"media_type":"person","name":"Someone Famous","profile_path":"/pp.jpg"},
              {"id":404,"media_type":"movie","title":"Mystery Reel","overview":"","poster_path":null,"backdrop_path":null,
               "release_date":"","vote_average":0}
            ]}""";

    /** /movie/popular: two plain movies, no media_type (the caller supplies the type). */
    public static final String POPULAR_MOVIES = """
            {"page":1,"total_pages":900,"total_results":18000,"results":[
              {"id":1,"title":"Alpha","overview":"a","poster_path":"/a.jpg","backdrop_path":"/ab.jpg","release_date":"2024-01-05","vote_average":6.5},
              {"id":2,"title":"Beta","overview":"b","poster_path":"/b.jpg","backdrop_path":null,"release_date":"2023-07-20","vote_average":7.2}
            ]}""";

    public static final String SEARCH_MULTI = """
            {"page":1,"total_pages":1,"total_results":4,"results":[
              {"id":525,"media_type":"person","name":"Christopher Nolan","profile_path":"/cn.jpg",
               "known_for":[{"id":1,"title":"Inception"},{"id":2,"title":"Interstellar"},{"id":3,"name":"A Show"},{"id":4,"title":"Fourth"}]},
              {"id":27205,"media_type":"movie","title":"Inception","overview":"Dreams.","poster_path":"/inc.jpg","release_date":"2010-07-15","vote_average":8.4},
              {"id":999,"media_type":"tv","name":"Nolan's Show","poster_path":null,"first_air_date":"2020-02-02","vote_average":5.0},
              {"id":888,"media_type":"collection","name":"Not Supported"}
            ]}""";

    public static final String GENRES_MOVIE = """
            {"genres":[{"id":28,"name":"Action"},{"id":18,"name":"Drama"},{"name":"Broken entry without id"},{"id":35,"name":""}]}""";

    /** /movie/550 with credits, videos, recommendations, similar and release_dates appended. */
    public static final String MOVIE_DETAIL = """
            {"id":550,"title":"Fight Club","tagline":"Mischief. Mayhem. Soap.","overview":"An insomniac office worker.",
             "poster_path":"/fc.jpg","backdrop_path":"/fcb.jpg","release_date":"1999-10-15","runtime":139,
             "vote_average":8.4,"vote_count":28000,"status":"Released","original_language":"en","homepage":"",
             "budget":63000000,"revenue":100853753,
             "genres":[{"id":18,"name":"Drama"},{"id":53,"name":"Thriller"}],
             "credits":{
               "cast":[{"id":819,"name":"Edward Norton","character":"The Narrator","profile_path":"/en.jpg"},
                       {"id":287,"name":"Brad Pitt","character":"Tyler Durden","profile_path":null}],
               "crew":[{"id":7467,"name":"David Fincher","job":"Director"},{"id":7468,"name":"Someone","job":"Producer"}]},
             "videos":{"results":[
               {"key":"teaser1","name":"Teaser","site":"YouTube","type":"Teaser","official":false},
               {"key":"trailer1","name":"Official Trailer","site":"YouTube","type":"Trailer","official":true},
               {"key":"clip1","name":"A Clip","site":"YouTube","type":"Clip","official":true},
               {"key":"vimeo1","name":"Vimeo Trailer","site":"Vimeo","type":"Trailer","official":true}]},
             "recommendations":{"results":[
               {"id":680,"title":"Pulp Fiction","poster_path":"/pf.jpg","backdrop_path":null,"release_date":"1994-09-10","vote_average":8.5},
               {"id":807,"title":"Se7en","poster_path":"/se.jpg","backdrop_path":null,"release_date":"1995-09-22","vote_average":8.4}]},
             "similar":{"results":[{"id":1,"title":"Should Not Be Used"}]},
             "release_dates":{"results":[
               {"iso_3166_1":"IN","release_dates":[{"certification":""},{"certification":"A"}]},
               {"iso_3166_1":"US","release_dates":[{"certification":"R"}]}]}}""";

    /** /tv/1396: no Indian rating (falls back to US), no recommendations (falls back to similar). */
    public static final String TV_DETAIL = """
            {"id":1396,"name":"Breaking Bad","tagline":"","overview":"A teacher turns to crime.",
             "poster_path":"/bb.jpg","backdrop_path":"/bbb.jpg","first_air_date":"2008-01-20","episode_run_time":[47,45],
             "vote_average":8.9,"vote_count":14000,"status":"Ended","original_language":"en","homepage":"https://example.test/bb",
             "number_of_seasons":5,"number_of_episodes":62,
             "genres":[{"id":18,"name":"Drama"}],
             "created_by":[{"id":66633,"name":"Vince Gilligan"}],
             "credits":{"cast":[{"id":17419,"name":"Bryan Cranston","character":"Walter White","profile_path":"/bc.jpg"}],"crew":[]},
             "videos":{"results":[]},
             "recommendations":{"results":[]},
             "similar":{"results":[{"id":60059,"name":"Better Call Saul","poster_path":"/bcs.jpg","first_air_date":"2015-02-08","vote_average":8.7}]},
             "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]}}""";
}
