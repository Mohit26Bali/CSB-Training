// Panel & Pixel - the AniList GraphQL query used for all live show data (one request fills the whole front page)

const ANILIST_URL = 'https://graphql.anilist.co';

const MEDIA_FIELDS = `
    id
    title { romaji english native }
    coverImage { large color }
    bannerImage
    format
    status
    episodes
    season
    seasonYear
    averageScore
    popularity
    genres
    siteUrl
    studios(isMain: true) { nodes { name } }
    nextAiringEpisode { episode airingAt }
    trailer { id site }
`;

const FRONT_QUERY = `
query ($season: MediaSeason, $year: Int, $nextSeason: MediaSeason, $nextYear: Int) {
    trending: Page(perPage: 10) {
        media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { ${MEDIA_FIELDS} }
    }
    season: Page(perPage: 12) {
        media(type: ANIME, season: $season, seasonYear: $year, sort: POPULARITY_DESC, isAdult: false) { ${MEDIA_FIELDS} }
    }
    upcoming: Page(perPage: 6) {
        media(type: ANIME, season: $nextSeason, seasonYear: $nextYear, sort: POPULARITY_DESC, isAdult: false) { ${MEDIA_FIELDS} }
    }
    favourites: Page(perPage: 2) {
        media(id_in: [101922, 5114], type: ANIME) {
            ${MEDIA_FIELDS}
            description(asHtml: false)
            rankings { rank type context allTime }
            favourites
        }
    }
    airing: Page(perPage: 50) {
        media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC, isAdult: false) {
            id
            title { romaji english }
            coverImage { large }
            siteUrl
            popularity
            nextAiringEpisode { episode airingAt }
        }
    }
}`;

const SHOW_QUERY = `
query ($id: Int) {
    Media(id: $id, type: ANIME) {
        ${MEDIA_FIELDS}
        description(asHtml: false)
        duration
        source
        startDate { year month day }
        rankings { rank type context allTime }
        favourites
        characters(sort: [ROLE, RELEVANCE], perPage: 6) {
            edges { role node { name { full } image { medium } } }
        }
    }
}`;
