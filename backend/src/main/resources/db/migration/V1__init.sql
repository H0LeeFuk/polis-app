-- Consolidated baseline schema + catalog seed data for POLIS (dev — not in production).
-- Regenerated from the live schema; collapses the former V1..V28 migrations into one file.
-- Catalog data: unit_types, bandit_camp_levels, missions. Runtime data is seeded by the app's
-- startup runners (WorldSeeder, NodeSeeder, AccountSetup, ...).

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alliance_forum_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alliance_forum_posts (
    id bigint NOT NULL,
    alliance_id bigint NOT NULL,
    author_player_id bigint NOT NULL,
    body character varying(2000) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: alliance_forum_posts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.alliance_forum_posts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: alliance_forum_posts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.alliance_forum_posts_id_seq OWNED BY public.alliance_forum_posts.id;


--
-- Name: alliance_invites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alliance_invites (
    id bigint NOT NULL,
    alliance_id bigint NOT NULL,
    player_id bigint NOT NULL,
    invited_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: alliance_invites_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.alliance_invites_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: alliance_invites_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.alliance_invites_id_seq OWNED BY public.alliance_invites.id;


--
-- Name: alliances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alliances (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    tag character varying(8) NOT NULL,
    name character varying(64) NOT NULL,
    leader_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    treasury_wood bigint DEFAULT 0 NOT NULL,
    treasury_stone bigint DEFAULT 0 NOT NULL,
    treasury_wheat bigint DEFAULT 0 NOT NULL
);


--
-- Name: alliances_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.alliances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: alliances_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.alliances_id_seq OWNED BY public.alliances.id;


--
-- Name: bandit_camp_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bandit_camp_levels (
    level integer NOT NULL,
    defender_troops jsonb DEFAULT '{}'::jsonb NOT NULL,
    reward_type character varying(12) DEFAULT 'RESOURCES'::character varying NOT NULL,
    reward_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    description character varying(200)
);


--
-- Name: bandit_camps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bandit_camps (
    id bigint NOT NULL,
    island_id bigint NOT NULL,
    current_level integer DEFAULT 1 NOT NULL,
    defeated_at timestamp with time zone,
    respawn_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    player_id bigint NOT NULL
);


--
-- Name: bandit_camps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bandit_camps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bandit_camps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bandit_camps_id_seq OWNED BY public.bandit_camps.id;


--
-- Name: battle_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battle_reports (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    movement_id bigint,
    fought_at timestamp with time zone DEFAULT now() NOT NULL,
    outcome character varying(16) NOT NULL,
    attacker_player_id bigint,
    attacker_city_id bigint,
    attacker_city_name character varying(128),
    attacker_player_name character varying(64),
    defender_player_id bigint,
    defender_city_id bigint,
    defender_city_name character varying(128),
    defender_player_name character varying(64),
    attacker_troops_sent jsonb DEFAULT '{}'::jsonb NOT NULL,
    attacker_troops_lost jsonb DEFAULT '{}'::jsonb NOT NULL,
    attacker_troops_survived jsonb DEFAULT '{}'::jsonb NOT NULL,
    defender_troops_present jsonb DEFAULT '{}'::jsonb NOT NULL,
    defender_troops_lost jsonb DEFAULT '{}'::jsonb NOT NULL,
    defender_troops_survived jsonb DEFAULT '{}'::jsonb NOT NULL,
    resources_stolen jsonb DEFAULT '{}'::jsonb NOT NULL,
    attacker_attack_power integer DEFAULT 0 NOT NULL,
    defender_defence_power integer DEFAULT 0 NOT NULL,
    attacker_read boolean DEFAULT false NOT NULL,
    defender_read boolean DEFAULT false NOT NULL,
    attacker_deleted boolean DEFAULT false NOT NULL,
    defender_deleted boolean DEFAULT false NOT NULL,
    siege_damage integer DEFAULT 0 NOT NULL,
    hero_name character varying(32),
    hero_level integer DEFAULT 0 NOT NULL,
    hero_attack_bonus_pct integer DEFAULT 0 NOT NULL,
    hero_loss_reduction_pct integer DEFAULT 0 NOT NULL,
    hero_skill_used character varying(24),
    hero_xp_gained integer DEFAULT 0 NOT NULL,
    hero_leveled_to integer,
    hero_wounded boolean DEFAULT false NOT NULL,
    attack_by_element json,
    defense_by_element json,
    combat_layer character varying(8) DEFAULT 'LAND'::character varying NOT NULL,
    combat_points_earned integer DEFAULT 0 NOT NULL,
    combat_points_reason character varying(160)
);


--
-- Name: battle_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.battle_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: battle_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.battle_reports_id_seq OWNED BY public.battle_reports.id;


--
-- Name: build_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.build_jobs (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    queue_type character varying(16) NOT NULL,
    building_type character varying(24),
    to_level integer,
    unit_type character varying(24),
    batch integer,
    "position" integer NOT NULL,
    started_at timestamp with time zone,
    finish_at timestamp with time zone,
    total_seconds integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: build_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.build_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: build_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.build_jobs_id_seq OWNED BY public.build_jobs.id;


--
-- Name: cities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cities (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    player_id bigint,
    island_id bigint NOT NULL,
    slot integer NOT NULL,
    name character varying(64) NOT NULL,
    is_capital boolean DEFAULT false NOT NULL,
    god character varying(16),
    wood double precision DEFAULT 0 NOT NULL,
    stone double precision DEFAULT 0 NOT NULL,
    wheat double precision DEFAULT 0 NOT NULL,
    power double precision DEFAULT 0 NOT NULL,
    points integer DEFAULT 0 NOT NULL,
    last_tick_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    race character varying(16),
    coal double precision DEFAULT 0 NOT NULL,
    crystals double precision DEFAULT 0 NOT NULL,
    iron double precision DEFAULT 0 NOT NULL,
    pearls double precision DEFAULT 0 NOT NULL
);


--
-- Name: cities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cities_id_seq OWNED BY public.cities.id;


--
-- Name: city_buildings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.city_buildings (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    type character varying(24) NOT NULL,
    level integer DEFAULT 0 NOT NULL
);


--
-- Name: city_buildings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.city_buildings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city_buildings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.city_buildings_id_seq OWNED BY public.city_buildings.id;


--
-- Name: city_library_research; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.city_library_research (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    research_id character varying(32) NOT NULL,
    status character varying(16) DEFAULT 'RESEARCHING'::character varying NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completes_at timestamp with time zone
);


--
-- Name: city_library_research_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.city_library_research_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city_library_research_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.city_library_research_id_seq OWNED BY public.city_library_research.id;


--
-- Name: city_research; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.city_research (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    type character varying(24) NOT NULL
);


--
-- Name: city_research_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.city_research_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city_research_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.city_research_id_seq OWNED BY public.city_research.id;


--
-- Name: city_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.city_units (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    type character varying(24) NOT NULL,
    count integer DEFAULT 0 NOT NULL
);


--
-- Name: city_units_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.city_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city_units_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.city_units_id_seq OWNED BY public.city_units.id;


--
-- Name: festivals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.festivals (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    player_id bigint NOT NULL,
    festival_type character varying(24) NOT NULL,
    fuel_type character varying(16) NOT NULL,
    status character varying(12) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completes_at timestamp with time zone NOT NULL,
    culture_points_reward integer NOT NULL
);


--
-- Name: festivals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.festivals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: festivals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.festivals_id_seq OWNED BY public.festivals.id;


--
-- Name: hero_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hero_items (
    id bigint NOT NULL,
    owner_player_id bigint NOT NULL,
    name character varying(64) NOT NULL,
    slot character varying(12) NOT NULL,
    rarity character varying(12) NOT NULL,
    buffs jsonb DEFAULT '{}'::jsonb NOT NULL,
    equipped boolean DEFAULT false NOT NULL,
    seen boolean DEFAULT false NOT NULL,
    obtained_at timestamp with time zone DEFAULT now() NOT NULL,
    special_effects jsonb DEFAULT '[]'::jsonb NOT NULL
);


--
-- Name: hero_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hero_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hero_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hero_items_id_seq OWNED BY public.hero_items.id;


--
-- Name: heroes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.heroes (
    id bigint NOT NULL,
    owner_player_id bigint NOT NULL,
    name character varying(32) NOT NULL,
    hero_key character varying(16) DEFAULT 'LEO'::character varying NOT NULL,
    race character varying(16) DEFAULT 'HUMANS'::character varying NOT NULL,
    unlocked boolean DEFAULT true NOT NULL,
    level integer DEFAULT 1 NOT NULL,
    current_xp bigint DEFAULT 0 NOT NULL,
    xp_to_next_level bigint DEFAULT 100 NOT NULL,
    unspent_attribute_points integer DEFAULT 0 NOT NULL,
    attr_leadership integer DEFAULT 0 NOT NULL,
    attr_cunning integer DEFAULT 0 NOT NULL,
    attr_valor integer DEFAULT 0 NOT NULL,
    state character varying(16) DEFAULT 'IDLE'::character varying NOT NULL,
    stationed_city_id bigint,
    active_movement_id bigint,
    wounded_until timestamp with time zone,
    unlocked_skills jsonb DEFAULT '[]'::jsonb NOT NULL,
    skill_cooldowns jsonb DEFAULT '{}'::jsonb NOT NULL,
    armed_skill character varying(24),
    equipped_weapon_id bigint,
    equipped_armor_id bigint,
    equipped_relic_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    equipped_pet_id bigint
);


--
-- Name: heroes_new_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.heroes_new_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: heroes_new_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.heroes_new_id_seq OWNED BY public.heroes.id;


--
-- Name: island_bosses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.island_bosses (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    island_id bigint NOT NULL,
    race character varying(16) NOT NULL,
    name character varying(48) NOT NULL,
    level integer DEFAULT 1 NOT NULL,
    defender_troops jsonb DEFAULT '{}'::jsonb NOT NULL,
    defeated_at timestamp with time zone,
    respawn_at timestamp with time zone
);


--
-- Name: island_bosses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.island_bosses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: island_bosses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.island_bosses_id_seq OWNED BY public.island_bosses.id;


--
-- Name: islands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.islands (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    name character varying(64) NOT NULL,
    ocean_x integer NOT NULL,
    ocean_y integer NOT NULL,
    px integer NOT NULL,
    py integer NOT NULL,
    seed bigint NOT NULL,
    is_resource boolean DEFAULT false NOT NULL,
    tier integer DEFAULT 0 NOT NULL
);


--
-- Name: islands_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.islands_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: islands_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.islands_id_seq OWNED BY public.islands.id;


--
-- Name: market_listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.market_listings (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    seller_player_id bigint NOT NULL,
    source_city_id bigint NOT NULL,
    resource_type character varying(8) NOT NULL,
    bundles integer NOT NULL,
    price_per_bundle integer NOT NULL,
    status character varying(12) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: market_listings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.market_listings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: market_listings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.market_listings_id_seq OWNED BY public.market_listings.id;


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id bigint NOT NULL,
    from_player_id bigint,
    to_player_id bigint,
    body character varying(1000) NOT NULL,
    sent_at timestamp with time zone DEFAULT now() NOT NULL,
    read_flag boolean DEFAULT false NOT NULL
);


--
-- Name: messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.messages_id_seq OWNED BY public.messages.id;


--
-- Name: missions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.missions (
    id bigint NOT NULL,
    chain character varying(24) DEFAULT 'STARTER'::character varying NOT NULL,
    order_index integer NOT NULL,
    title character varying(80) NOT NULL,
    description text,
    objective_type character varying(32) NOT NULL,
    objective_target integer DEFAULT 1 NOT NULL,
    objective_params jsonb DEFAULT '{}'::jsonb NOT NULL,
    rewards jsonb DEFAULT '{}'::jsonb NOT NULL,
    prerequisite_mission_id bigint,
    unlocks_hero_key character varying(16)
);


--
-- Name: missions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.missions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: missions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.missions_id_seq OWNED BY public.missions.id;


--
-- Name: movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.movements (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    player_id bigint,
    source_city_id bigint,
    target_city_id bigint,
    target_island_id bigint,
    target_slot integer,
    phase character varying(16) NOT NULL,
    units jsonb DEFAULT '{}'::jsonb NOT NULL,
    loot jsonb,
    depart_at timestamp with time zone DEFAULT now() NOT NULL,
    arrive_at timestamp with time zone NOT NULL,
    resolved boolean DEFAULT false NOT NULL,
    target_node_id bigint,
    arrived_at timestamp with time zone,
    target_camp_id bigint
);


--
-- Name: movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.movements_id_seq OWNED BY public.movements.id;


--
-- Name: player_missions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.player_missions (
    id bigint NOT NULL,
    player_id bigint NOT NULL,
    mission_id bigint NOT NULL,
    status character varying(16) DEFAULT 'LOCKED'::character varying NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    completed_at timestamp with time zone
);


--
-- Name: player_missions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.player_missions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: player_missions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.player_missions_id_seq OWNED BY public.player_missions.id;


--
-- Name: players; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.players (
    id bigint NOT NULL,
    username character varying(32) NOT NULL,
    email character varying(128),
    password_hash character varying(100) NOT NULL,
    world_id bigint NOT NULL,
    alliance_id bigint,
    level integer DEFAULT 1 NOT NULL,
    combat_points integer DEFAULT 0 NOT NULL,
    combat_points_total integer DEFAULT 0 NOT NULL,
    is_npc boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    gold integer DEFAULT 500 NOT NULL,
    culture_points integer DEFAULT 0 NOT NULL,
    culture_points_total integer DEFAULT 0 NOT NULL
);


--
-- Name: players_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.players_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: players_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.players_id_seq OWNED BY public.players.id;


--
-- Name: resource_nodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resource_nodes (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    island_id bigint NOT NULL,
    x integer DEFAULT 0 NOT NULL,
    y integer DEFAULT 0 NOT NULL,
    node_type character varying(20) NOT NULL,
    level integer DEFAULT 1 NOT NULL,
    status character varying(12) DEFAULT 'UNCLAIMED'::character varying NOT NULL,
    controlling_player_id bigint,
    controlling_alliance_id bigint,
    garrison jsonb DEFAULT '{}'::jsonb NOT NULL,
    accumulated_resources bigint DEFAULT 0 NOT NULL,
    last_tick_at timestamp with time zone DEFAULT now() NOT NULL,
    claimed_at timestamp with time zone,
    contested_until timestamp with time zone
);


--
-- Name: resource_nodes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.resource_nodes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: resource_nodes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.resource_nodes_id_seq OWNED BY public.resource_nodes.id;


--
-- Name: trade_convoys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trade_convoys (
    id bigint NOT NULL,
    world_id bigint NOT NULL,
    buyer_player_id bigint NOT NULL,
    seller_player_id bigint,
    origin_city_id bigint NOT NULL,
    destination_city_id bigint NOT NULL,
    cargo jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(12) DEFAULT 'PENDING'::character varying NOT NULL,
    depart_at timestamp with time zone,
    arrive_at timestamp with time zone,
    seen boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trade_convoys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trade_convoys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trade_convoys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trade_convoys_id_seq OWNED BY public.trade_convoys.id;


--
-- Name: unit_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unit_types (
    id bigint NOT NULL,
    name character varying(32) NOT NULL,
    attack integer DEFAULT 0 NOT NULL,
    speed_minutes_per_tile integer DEFAULT 20 NOT NULL,
    carry_capacity integer DEFAULT 0 NOT NULL,
    population_cost integer DEFAULT 1 NOT NULL,
    kind character varying(8) DEFAULT 'LAND'::character varying NOT NULL,
    from_queue character varying(16) DEFAULT 'BARRACKS'::character varying NOT NULL,
    train_seconds integer DEFAULT 15 NOT NULL,
    cost_wood integer DEFAULT 0 NOT NULL,
    cost_stone integer DEFAULT 0 NOT NULL,
    cost_wheat integer DEFAULT 0 NOT NULL,
    research_required character varying(32),
    race character varying(16),
    movement_class character varying(12) DEFAULT 'LAND'::character varying NOT NULL,
    transport_capacity integer DEFAULT 0 NOT NULL,
    cost_special integer DEFAULT 0 NOT NULL,
    attack_element character varying(8),
    is_siege boolean DEFAULT false NOT NULL,
    defense_fire integer DEFAULT 0 NOT NULL,
    defense_wind integer DEFAULT 0 NOT NULL,
    defense_earth integer DEFAULT 0 NOT NULL,
    defense_water integer DEFAULT 0 NOT NULL,
    combat_layer character varying(8) DEFAULT 'LAND'::character varying NOT NULL,
    ship_role character varying(12)
);


--
-- Name: unit_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.unit_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: unit_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.unit_types_id_seq OWNED BY public.unit_types.id;


--
-- Name: worlds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.worlds (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    speed integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: worlds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.worlds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: worlds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.worlds_id_seq OWNED BY public.worlds.id;


--
-- Name: alliance_forum_posts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_forum_posts ALTER COLUMN id SET DEFAULT nextval('public.alliance_forum_posts_id_seq'::regclass);


--
-- Name: alliance_invites id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites ALTER COLUMN id SET DEFAULT nextval('public.alliance_invites_id_seq'::regclass);


--
-- Name: alliances id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliances ALTER COLUMN id SET DEFAULT nextval('public.alliances_id_seq'::regclass);


--
-- Name: bandit_camps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bandit_camps ALTER COLUMN id SET DEFAULT nextval('public.bandit_camps_id_seq'::regclass);


--
-- Name: battle_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_reports ALTER COLUMN id SET DEFAULT nextval('public.battle_reports_id_seq'::regclass);


--
-- Name: build_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.build_jobs ALTER COLUMN id SET DEFAULT nextval('public.build_jobs_id_seq'::regclass);


--
-- Name: cities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities ALTER COLUMN id SET DEFAULT nextval('public.cities_id_seq'::regclass);


--
-- Name: city_buildings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_buildings ALTER COLUMN id SET DEFAULT nextval('public.city_buildings_id_seq'::regclass);


--
-- Name: city_library_research id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_library_research ALTER COLUMN id SET DEFAULT nextval('public.city_library_research_id_seq'::regclass);


--
-- Name: city_research id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_research ALTER COLUMN id SET DEFAULT nextval('public.city_research_id_seq'::regclass);


--
-- Name: city_units id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_units ALTER COLUMN id SET DEFAULT nextval('public.city_units_id_seq'::regclass);


--
-- Name: festivals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.festivals ALTER COLUMN id SET DEFAULT nextval('public.festivals_id_seq'::regclass);


--
-- Name: hero_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hero_items ALTER COLUMN id SET DEFAULT nextval('public.hero_items_id_seq'::regclass);


--
-- Name: heroes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.heroes ALTER COLUMN id SET DEFAULT nextval('public.heroes_new_id_seq'::regclass);


--
-- Name: island_bosses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.island_bosses ALTER COLUMN id SET DEFAULT nextval('public.island_bosses_id_seq'::regclass);


--
-- Name: islands id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.islands ALTER COLUMN id SET DEFAULT nextval('public.islands_id_seq'::regclass);


--
-- Name: market_listings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_listings ALTER COLUMN id SET DEFAULT nextval('public.market_listings_id_seq'::regclass);


--
-- Name: messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages ALTER COLUMN id SET DEFAULT nextval('public.messages_id_seq'::regclass);


--
-- Name: missions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.missions ALTER COLUMN id SET DEFAULT nextval('public.missions_id_seq'::regclass);


--
-- Name: movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements ALTER COLUMN id SET DEFAULT nextval('public.movements_id_seq'::regclass);


--
-- Name: player_missions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_missions ALTER COLUMN id SET DEFAULT nextval('public.player_missions_id_seq'::regclass);


--
-- Name: players id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players ALTER COLUMN id SET DEFAULT nextval('public.players_id_seq'::regclass);


--
-- Name: resource_nodes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_nodes ALTER COLUMN id SET DEFAULT nextval('public.resource_nodes_id_seq'::regclass);


--
-- Name: trade_convoys id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys ALTER COLUMN id SET DEFAULT nextval('public.trade_convoys_id_seq'::regclass);


--
-- Name: unit_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unit_types ALTER COLUMN id SET DEFAULT nextval('public.unit_types_id_seq'::regclass);


--
-- Name: worlds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worlds ALTER COLUMN id SET DEFAULT nextval('public.worlds_id_seq'::regclass);


--
-- Name: alliance_forum_posts alliance_forum_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_forum_posts
    ADD CONSTRAINT alliance_forum_posts_pkey PRIMARY KEY (id);


--
-- Name: alliance_invites alliance_invites_alliance_id_player_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites
    ADD CONSTRAINT alliance_invites_alliance_id_player_id_key UNIQUE (alliance_id, player_id);


--
-- Name: alliance_invites alliance_invites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites
    ADD CONSTRAINT alliance_invites_pkey PRIMARY KEY (id);


--
-- Name: alliances alliances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliances
    ADD CONSTRAINT alliances_pkey PRIMARY KEY (id);


--
-- Name: bandit_camp_levels bandit_camp_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bandit_camp_levels
    ADD CONSTRAINT bandit_camp_levels_pkey PRIMARY KEY (level);


--
-- Name: bandit_camps bandit_camps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bandit_camps
    ADD CONSTRAINT bandit_camps_pkey PRIMARY KEY (id);


--
-- Name: battle_reports battle_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_reports
    ADD CONSTRAINT battle_reports_pkey PRIMARY KEY (id);


--
-- Name: build_jobs build_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.build_jobs
    ADD CONSTRAINT build_jobs_pkey PRIMARY KEY (id);


--
-- Name: cities cities_island_id_slot_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_island_id_slot_key UNIQUE (island_id, slot);


--
-- Name: cities cities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (id);


--
-- Name: city_buildings city_buildings_city_id_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_buildings
    ADD CONSTRAINT city_buildings_city_id_type_key UNIQUE (city_id, type);


--
-- Name: city_buildings city_buildings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_buildings
    ADD CONSTRAINT city_buildings_pkey PRIMARY KEY (id);


--
-- Name: city_library_research city_library_research_city_id_research_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_library_research
    ADD CONSTRAINT city_library_research_city_id_research_id_key UNIQUE (city_id, research_id);


--
-- Name: city_library_research city_library_research_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_library_research
    ADD CONSTRAINT city_library_research_pkey PRIMARY KEY (id);


--
-- Name: city_research city_research_city_id_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_research
    ADD CONSTRAINT city_research_city_id_type_key UNIQUE (city_id, type);


--
-- Name: city_research city_research_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_research
    ADD CONSTRAINT city_research_pkey PRIMARY KEY (id);


--
-- Name: city_units city_units_city_id_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_units
    ADD CONSTRAINT city_units_city_id_type_key UNIQUE (city_id, type);


--
-- Name: city_units city_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_units
    ADD CONSTRAINT city_units_pkey PRIMARY KEY (id);


--
-- Name: festivals festivals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.festivals
    ADD CONSTRAINT festivals_pkey PRIMARY KEY (id);


--
-- Name: hero_items hero_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hero_items
    ADD CONSTRAINT hero_items_pkey PRIMARY KEY (id);


--
-- Name: heroes heroes_new_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.heroes
    ADD CONSTRAINT heroes_new_pkey PRIMARY KEY (id);


--
-- Name: island_bosses island_bosses_island_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.island_bosses
    ADD CONSTRAINT island_bosses_island_id_key UNIQUE (island_id);


--
-- Name: island_bosses island_bosses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.island_bosses
    ADD CONSTRAINT island_bosses_pkey PRIMARY KEY (id);


--
-- Name: islands islands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.islands
    ADD CONSTRAINT islands_pkey PRIMARY KEY (id);


--
-- Name: market_listings market_listings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_listings
    ADD CONSTRAINT market_listings_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: missions missions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.missions
    ADD CONSTRAINT missions_pkey PRIMARY KEY (id);


--
-- Name: movements movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_pkey PRIMARY KEY (id);


--
-- Name: player_missions player_missions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_missions
    ADD CONSTRAINT player_missions_pkey PRIMARY KEY (id);


--
-- Name: player_missions player_missions_player_id_mission_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_missions
    ADD CONSTRAINT player_missions_player_id_mission_id_key UNIQUE (player_id, mission_id);


--
-- Name: players players_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_email_key UNIQUE (email);


--
-- Name: players players_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_pkey PRIMARY KEY (id);


--
-- Name: players players_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_username_key UNIQUE (username);


--
-- Name: resource_nodes resource_nodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_nodes
    ADD CONSTRAINT resource_nodes_pkey PRIMARY KEY (id);


--
-- Name: trade_convoys trade_convoys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_pkey PRIMARY KEY (id);


--
-- Name: unit_types unit_types_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unit_types
    ADD CONSTRAINT unit_types_name_key UNIQUE (name);


--
-- Name: unit_types unit_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unit_types
    ADD CONSTRAINT unit_types_pkey PRIMARY KEY (id);


--
-- Name: bandit_camps uq_bandit_camp_island_player; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bandit_camps
    ADD CONSTRAINT uq_bandit_camp_island_player UNIQUE (island_id, player_id);


--
-- Name: worlds worlds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worlds
    ADD CONSTRAINT worlds_pkey PRIMARY KEY (id);


--
-- Name: idx_cities_player; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cities_player ON public.cities USING btree (player_id);


--
-- Name: idx_cities_world; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cities_world ON public.cities USING btree (world_id);


--
-- Name: idx_clr_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clr_city ON public.city_library_research USING btree (city_id);


--
-- Name: idx_convoy_buyer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_convoy_buyer ON public.trade_convoys USING btree (buyer_player_id, status);


--
-- Name: idx_convoy_dest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_convoy_dest ON public.trade_convoys USING btree (destination_city_id, status);


--
-- Name: idx_convoy_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_convoy_due ON public.trade_convoys USING btree (status, arrive_at);


--
-- Name: idx_festivals_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_festivals_city ON public.festivals USING btree (city_id);


--
-- Name: idx_festivals_status_completes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_festivals_status_completes ON public.festivals USING btree (status, completes_at);


--
-- Name: idx_forum_alliance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_alliance ON public.alliance_forum_posts USING btree (alliance_id, created_at);


--
-- Name: idx_hero_active_move; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hero_active_move ON public.heroes USING btree (active_movement_id);


--
-- Name: idx_invite_player; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invite_player ON public.alliance_invites USING btree (player_id);


--
-- Name: idx_items_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_items_owner ON public.hero_items USING btree (owner_player_id);


--
-- Name: idx_jobs_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jobs_city ON public.build_jobs USING btree (city_id);


--
-- Name: idx_jobs_finish; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jobs_finish ON public.build_jobs USING btree (finish_at);


--
-- Name: idx_listings_book; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_book ON public.market_listings USING btree (resource_type, status, price_per_bundle);


--
-- Name: idx_listings_seller; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_seller ON public.market_listings USING btree (seller_player_id, status);


--
-- Name: idx_moves_arrive; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_moves_arrive ON public.movements USING btree (arrive_at);


--
-- Name: idx_msg_to; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_msg_to ON public.messages USING btree (to_player_id);


--
-- Name: idx_nodes_controller; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nodes_controller ON public.resource_nodes USING btree (controlling_player_id);


--
-- Name: idx_nodes_island; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nodes_island ON public.resource_nodes USING btree (island_id);


--
-- Name: idx_nodes_world; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nodes_world ON public.resource_nodes USING btree (world_id);


--
-- Name: idx_players_world; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_players_world ON public.players USING btree (world_id);


--
-- Name: idx_reports_atk_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_atk_city ON public.battle_reports USING btree (attacker_city_id);


--
-- Name: idx_reports_attacker; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_attacker ON public.battle_reports USING btree (attacker_player_id, fought_at DESC);


--
-- Name: idx_reports_def_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_def_city ON public.battle_reports USING btree (defender_city_id);


--
-- Name: idx_reports_defender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_defender ON public.battle_reports USING btree (defender_player_id, fought_at DESC);


--
-- Name: alliance_forum_posts alliance_forum_posts_alliance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_forum_posts
    ADD CONSTRAINT alliance_forum_posts_alliance_id_fkey FOREIGN KEY (alliance_id) REFERENCES public.alliances(id);


--
-- Name: alliance_forum_posts alliance_forum_posts_author_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_forum_posts
    ADD CONSTRAINT alliance_forum_posts_author_player_id_fkey FOREIGN KEY (author_player_id) REFERENCES public.players(id);


--
-- Name: alliance_invites alliance_invites_alliance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites
    ADD CONSTRAINT alliance_invites_alliance_id_fkey FOREIGN KEY (alliance_id) REFERENCES public.alliances(id);


--
-- Name: alliance_invites alliance_invites_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites
    ADD CONSTRAINT alliance_invites_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.players(id);


--
-- Name: alliance_invites alliance_invites_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliance_invites
    ADD CONSTRAINT alliance_invites_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id);


--
-- Name: alliances alliances_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliances
    ADD CONSTRAINT alliances_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: bandit_camps bandit_camps_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bandit_camps
    ADD CONSTRAINT bandit_camps_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id);


--
-- Name: build_jobs build_jobs_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.build_jobs
    ADD CONSTRAINT build_jobs_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE CASCADE;


--
-- Name: cities cities_island_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_island_id_fkey FOREIGN KEY (island_id) REFERENCES public.islands(id);


--
-- Name: cities cities_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id);


--
-- Name: cities cities_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: city_buildings city_buildings_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_buildings
    ADD CONSTRAINT city_buildings_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE CASCADE;


--
-- Name: city_library_research city_library_research_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_library_research
    ADD CONSTRAINT city_library_research_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id);


--
-- Name: city_research city_research_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_research
    ADD CONSTRAINT city_research_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE CASCADE;


--
-- Name: city_units city_units_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city_units
    ADD CONSTRAINT city_units_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE CASCADE;


--
-- Name: alliances fk_alliance_leader; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alliances
    ADD CONSTRAINT fk_alliance_leader FOREIGN KEY (leader_id) REFERENCES public.players(id);


--
-- Name: hero_items hero_items_owner_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hero_items
    ADD CONSTRAINT hero_items_owner_player_id_fkey FOREIGN KEY (owner_player_id) REFERENCES public.players(id);


--
-- Name: heroes heroes_new_owner_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.heroes
    ADD CONSTRAINT heroes_new_owner_player_id_fkey FOREIGN KEY (owner_player_id) REFERENCES public.players(id);


--
-- Name: island_bosses island_bosses_island_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.island_bosses
    ADD CONSTRAINT island_bosses_island_id_fkey FOREIGN KEY (island_id) REFERENCES public.islands(id);


--
-- Name: islands islands_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.islands
    ADD CONSTRAINT islands_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: market_listings market_listings_seller_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_listings
    ADD CONSTRAINT market_listings_seller_player_id_fkey FOREIGN KEY (seller_player_id) REFERENCES public.players(id);


--
-- Name: market_listings market_listings_source_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_listings
    ADD CONSTRAINT market_listings_source_city_id_fkey FOREIGN KEY (source_city_id) REFERENCES public.cities(id);


--
-- Name: market_listings market_listings_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_listings
    ADD CONSTRAINT market_listings_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: messages messages_from_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_from_player_id_fkey FOREIGN KEY (from_player_id) REFERENCES public.players(id);


--
-- Name: messages messages_to_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_to_player_id_fkey FOREIGN KEY (to_player_id) REFERENCES public.players(id);


--
-- Name: movements movements_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id);


--
-- Name: movements movements_source_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_source_city_id_fkey FOREIGN KEY (source_city_id) REFERENCES public.cities(id);


--
-- Name: movements movements_target_camp_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_target_camp_id_fkey FOREIGN KEY (target_camp_id) REFERENCES public.bandit_camps(id);


--
-- Name: movements movements_target_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_target_city_id_fkey FOREIGN KEY (target_city_id) REFERENCES public.cities(id);


--
-- Name: movements movements_target_island_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_target_island_id_fkey FOREIGN KEY (target_island_id) REFERENCES public.islands(id);


--
-- Name: movements movements_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: player_missions player_missions_mission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_missions
    ADD CONSTRAINT player_missions_mission_id_fkey FOREIGN KEY (mission_id) REFERENCES public.missions(id);


--
-- Name: player_missions player_missions_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_missions
    ADD CONSTRAINT player_missions_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id);


--
-- Name: players players_alliance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_alliance_id_fkey FOREIGN KEY (alliance_id) REFERENCES public.alliances(id);


--
-- Name: players players_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- Name: trade_convoys trade_convoys_buyer_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_buyer_player_id_fkey FOREIGN KEY (buyer_player_id) REFERENCES public.players(id);


--
-- Name: trade_convoys trade_convoys_destination_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_destination_city_id_fkey FOREIGN KEY (destination_city_id) REFERENCES public.cities(id);


--
-- Name: trade_convoys trade_convoys_origin_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_origin_city_id_fkey FOREIGN KEY (origin_city_id) REFERENCES public.cities(id);


--
-- Name: trade_convoys trade_convoys_seller_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_seller_player_id_fkey FOREIGN KEY (seller_player_id) REFERENCES public.players(id);


--
-- Name: trade_convoys trade_convoys_world_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_convoys
    ADD CONSTRAINT trade_convoys_world_id_fkey FOREIGN KEY (world_id) REFERENCES public.worlds(id);


--
-- PostgreSQL database dump complete
--



-- ===== catalog seed data =====
--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: bandit_camp_levels; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.bandit_camp_levels VALUES (1, '{"SWORDSMAN": 10}', 'RESOURCES', '{"wood": 200, "stone": 100}', 'A handful of armed deserters guard their stash.');
INSERT INTO public.bandit_camp_levels VALUES (2, '{"ARCHER": 5, "SWORDSMAN": 15}', 'RESOURCES', '{"wood": 300, "silver": 200}', 'Brigands with a few bowmen watch the road.');
INSERT INTO public.bandit_camp_levels VALUES (3, '{"ARCHER": 10, "SWORDSMAN": 20}', 'RESOURCES', '{"stone": 500, "silver": 100}', 'A fortified bandit outpost.');
INSERT INTO public.bandit_camp_levels VALUES (4, '{"ARCHER": 15, "HORSEMAN": 5, "SWORDSMAN": 25}', 'TROOPS', '{"ARCHER": 1}', 'Mounted raiders have joined the camp.');
INSERT INTO public.bandit_camp_levels VALUES (5, '{"ARCHER": 20, "HORSEMAN": 10, "SWORDSMAN": 30}', 'RESOURCES', '{"wood": 800, "silver": 400}', 'A war-band of seasoned marauders.');
INSERT INTO public.bandit_camp_levels VALUES (6, '{"ARCHER": 10, "CATAPULT": 5, "HORSEMAN": 5, "SWORDSMAN": 20}', 'TROOPS', '{"HORSEMAN": 2}', 'Siege engines loom over the palisade.');
INSERT INTO public.bandit_camp_levels VALUES (7, '{"ARCHER": 15, "CATAPULT": 10, "SWORDSMAN": 25}', 'RESOURCES', '{"wood": 1000, "stone": 800, "silver": 300}', 'A bandit stronghold bristling with catapults.');
INSERT INTO public.bandit_camp_levels VALUES (8, '{"ARCHER": 15, "CATAPULT": 15, "HORSEMAN": 10, "SWORDSMAN": 30}', 'TROOPS', '{"CATAPULT": 3}', 'A fortress of the bandit king''s lieutenants.');
INSERT INTO public.bandit_camp_levels VALUES (9, '{"ARCHER": 20, "CATAPULT": 20, "HORSEMAN": 20, "SWORDSMAN": 40}', 'RESOURCES', '{"wood": 1000, "silver": 2000}', 'The bandit king''s elite host.');
INSERT INTO public.bandit_camp_levels VALUES (10, '{"ARCHER": 25, "CATAPULT": 15, "HORSEMAN": 20, "SWORDSMAN": 40}', 'MIXED', '{"wood": 1500, "stone": 1500, "silver": 1500, "HORSEMAN": 5}', 'The bandit king himself, with his finest warriors.');


--
-- Data for Name: missions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.missions VALUES (1, 'STARTER', 1, 'First Foundations', 'Construct 2 buildings in your city.', 'BUILD_BUILDING', 2, '{}', '{"wood": 150, "stone": 150}', NULL, NULL);
INSERT INTO public.missions VALUES (2, 'STARTER', 2, 'A Growing Polis', 'Raise any building to level 3.', 'UPGRADE_BUILDING_LEVEL', 3, '{}', '{"wood": 200, "stone": 200}', 1, NULL);
INSERT INTO public.missions VALUES (3, 'STARTER', 3, 'Raise an Army', 'Train 10 troops.', 'TRAIN_TROOPS', 10, '{}', '{"silver": 200}', 2, NULL);
INSERT INTO public.missions VALUES (4, 'STARTER', 4, 'Blood the Blade', 'Defeat a bandit camp.', 'ATTACK_BANDIT_CAMP', 1, '{}', '{"wood": 200, "stone": 200, "heroXp": 50}', 3, NULL);
INSERT INTO public.missions VALUES (6, 'STARTER', 6, 'The Spoils of War', 'Launch an attack on another player.', 'ATTACK_PLAYER', 1, '{}', '{"heroXp": 50, "silver": 300}', 5, NULL);
INSERT INTO public.missions VALUES (7, 'STARTER', 7, 'Expand the Realm', 'Found your second city.', 'FOUND_CITY', 1, '{}', '{"wood": 300, "stone": 300, "silver": 200}', 6, NULL);
INSERT INTO public.missions VALUES (5, 'STARTER', 5, 'Knowledge is Power', 'Raise your Library to level 2.', 'REACH_LIBRARY_LEVEL', 2, '{}', '{"silver": 250}', 4, NULL);
INSERT INTO public.missions VALUES (8, 'STARTER', 8, 'A New Ally Arrives', 'Complete the starter trials to recruit Titania the Fairy.', 'CHAIN_COMPLETE', 1, '{}', '{"wood": 500, "stone": 500, "silver": 300}', 7, 'TITANIA');


--
-- Data for Name: unit_types; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.unit_types VALUES (3, 'SPEARMAN', 50, 22, 25, 1, 'LAND', 'BARRACKS', 18, 70, 50, 30, NULL, 'HUMANS', 'LAND', 0, 0, NULL, false, 55, 45, 40, 45, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (4, 'ARCHER', 60, 18, 30, 1, 'LAND', 'BARRACKS', 20, 80, 30, 60, NULL, 'HUMANS', 'LAND', 0, 0, NULL, false, 40, 60, 35, 45, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (5, 'HORSEMAN', 110, 12, 80, 4, 'LAND', 'BARRACKS', 40, 160, 100, 140, NULL, 'HUMANS', 'LAND', 0, 0, NULL, false, 35, 35, 30, 30, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (6, 'CATAPULT', 200, 60, 30, 8, 'LAND', 'BARRACKS', 60, 380, 520, 220, 'CATAPULT', 'HUMANS', 'LAND', 0, 0, NULL, true, 20, 20, 20, 20, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (23, 'FLAME_LEGION', 150, 20, 40, 3, 'LAND', 'BARRACKS', 50, 220, 160, 120, NULL, 'HUMANS', 'LAND', 0, 30, 'FIRE', false, 90, 70, 60, 45, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (24, 'EARTHSHAKER', 170, 26, 50, 4, 'LAND', 'BARRACKS', 60, 260, 220, 140, NULL, 'GIANTS', 'LAND', 0, 35, 'EARTH', false, 60, 55, 95, 70, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (21, 'TIDE_RAIDER', 120, 12, 300, 3, 'SEA', 'HARBOR', 45, 220, 120, 260, NULL, 'NEWTS', 'SWIMMING', 0, 0, NULL, false, 40, 35, 45, 65, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (20, 'SNAPPER', 140, 14, 250, 4, 'SEA', 'HARBOR', 40, 260, 180, 140, NULL, 'NEWTS', 'SWIMMING', 0, 0, NULL, false, 45, 40, 50, 70, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (22, 'LEVIATHAN', 300, 12, 400, 10, 'SEA', 'HARBOR', 90, 600, 400, 500, NULL, 'NEWTS', 'SWIMMING', 0, 0, NULL, false, 60, 50, 65, 92, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (17, 'DRAGONFLY_SKIFF', 100, 10, 260, 4, 'SEA', 'BARRACKS', 35, 200, 80, 260, NULL, 'FAIRIES', 'FLYING', 0, 0, NULL, false, 30, 55, 30, 35, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (7, 'TRIREME', 30, 18, 0, 4, 'SEA', 'HARBOR', 45, 300, 120, 180, NULL, 'HUMANS', 'SWIMMING', 0, 0, NULL, false, 55, 55, 50, 75, 'SEA', 'DEFENSE');
INSERT INTO public.unit_types VALUES (27, 'GALLEY', 0, 18, 0, 5, 'SEA', 'HARBOR', 30, 200, 120, 80, NULL, 'HUMANS', 'SWIMMING', 30, 0, NULL, false, 25, 25, 25, 35, 'SEA', 'TRANSPORT');
INSERT INTO public.unit_types VALUES (28, 'FIRE_RAM', 70, 14, 40, 5, 'SEA', 'HARBOR', 45, 260, 160, 120, NULL, 'HUMANS', 'SWIMMING', 0, 0, NULL, false, 35, 30, 30, 45, 'SEA', 'ATTACK');
INSERT INTO public.unit_types VALUES (12, 'WAR_BARGE', 0, 35, 300, 12, 'SEA', 'HARBOR', 70, 500, 400, 200, NULL, 'GIANTS', 'SWIMMING', 60, 0, NULL, false, 50, 45, 65, 55, 'SEA', 'TRANSPORT');
INSERT INTO public.unit_types VALUES (29, 'BULWARK_SHIP', 90, 35, 30, 14, 'SEA', 'HARBOR', 70, 500, 400, 200, NULL, 'GIANTS', 'SWIMMING', 0, 0, NULL, false, 70, 65, 95, 80, 'SEA', 'DEFENSE');
INSERT INTO public.unit_types VALUES (30, 'SIEGE_GALLEON', 180, 26, 60, 16, 'SEA', 'HARBOR', 90, 700, 520, 300, NULL, 'GIANTS', 'SWIMMING', 0, 0, NULL, false, 60, 55, 80, 70, 'SEA', 'ATTACK');
INSERT INTO public.unit_types VALUES (26, 'LEVIATHAN_RIDER', 165, 18, 60, 4, 'LAND', 'HARBOR', 58, 240, 170, 150, NULL, 'NEWTS', 'SWIMMING', 0, 34, 'WATER', false, 60, 50, 65, 95, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (18, 'MUDLING', 35, 26, 15, 1, 'LAND', 'HARBOR', 16, 60, 50, 20, NULL, 'NEWTS', 'SWIMMING', 0, 0, NULL, false, 25, 25, 30, 45, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (19, 'NEWT_SPEAR', 60, 24, 20, 1, 'LAND', 'HARBOR', 18, 70, 60, 30, NULL, 'NEWTS', 'SWIMMING', 0, 0, NULL, false, 40, 35, 35, 55, 'SEA', NULL);
INSERT INTO public.unit_types VALUES (25, 'STORMCALLER', 175, 14, 35, 3, 'LAND', 'BARRACKS', 55, 240, 150, 140, NULL, 'FAIRIES', 'FLYING', 0, 32, 'WIND', false, 55, 95, 45, 65, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (1, 'HOPLITE', 16, 30, 10, 1, 'LAND', 'BARRACKS', 15, 60, 30, 30, NULL, 'HUMANS', 'LAND', 0, 0, NULL, false, 60, 55, 75, 50, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (2, 'SWORDSMAN', 55, 22, 20, 1, 'LAND', 'BARRACKS', 18, 80, 40, 30, NULL, 'HUMANS', 'LAND', 0, 0, NULL, false, 35, 30, 40, 28, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (15, 'GLIMMER_GUARD', 30, 10, 10, 1, 'LAND', 'BARRACKS', 14, 60, 40, 40, NULL, 'FAIRIES', 'FLYING', 0, 0, NULL, false, 45, 65, 40, 45, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (13, 'SPRITE', 45, 8, 15, 1, 'LAND', 'BARRACKS', 12, 50, 20, 40, NULL, 'FAIRIES', 'FLYING', 0, 0, NULL, false, 25, 45, 20, 30, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (14, 'PIXIE_ARCHER', 80, 7, 20, 1, 'LAND', 'BARRACKS', 16, 70, 20, 70, NULL, 'FAIRIES', 'FLYING', 0, 0, NULL, false, 20, 42, 18, 28, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (16, 'MOTH_RIDER', 130, 5, 120, 3, 'LAND', 'BARRACKS', 30, 140, 60, 180, NULL, 'FAIRIES', 'FLYING', 0, 0, NULL, false, 30, 52, 28, 34, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (8, 'BOULDER_THROWER', 90, 40, 20, 3, 'LAND', 'BARRACKS', 35, 180, 220, 60, NULL, 'GIANTS', 'LAND', 0, 0, NULL, false, 40, 35, 60, 45, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (9, 'TROLL', 120, 40, 60, 4, 'LAND', 'BARRACKS', 45, 220, 180, 80, NULL, 'GIANTS', 'LAND', 0, 0, NULL, false, 55, 45, 70, 50, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (10, 'STONE_GIANT', 180, 45, 40, 6, 'LAND', 'BARRACKS', 60, 300, 400, 120, NULL, 'GIANTS', 'LAND', 0, 0, NULL, false, 65, 55, 90, 60, 'LAND', NULL);
INSERT INTO public.unit_types VALUES (11, 'COLOSSUS', 360, 70, 50, 12, 'LAND', 'BARRACKS', 120, 700, 900, 400, 'CATAPULT', 'GIANTS', 'LAND', 0, 0, NULL, false, 70, 60, 100, 65, 'LAND', NULL);


--
-- Name: missions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.missions_id_seq', 8, true);


--
-- Name: unit_types_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.unit_types_id_seq', 30, true);


--
-- PostgreSQL database dump complete
--


-- ===== endgame: Wonders of the Aegean =====

-- one row per world; tracks the GROWTH -> ENDGAME -> FINISHED lifecycle and the win timer
CREATE TABLE public.world_state (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    world_id bigint NOT NULL UNIQUE REFERENCES public.worlds(id),
    phase character varying(16) NOT NULL DEFAULT 'GROWTH',
    endgame_started_at timestamp with time zone,
    consolidation_started_at timestamp with time zone,
    consolidation_alliance_id bigint,
    winner_alliance_id bigint,
    finished_at timestamp with time zone
);

-- the 3 Wonder Islands' wonders; militarily controlled like nodes, alliance-pooled to raise level
CREATE TABLE public.wonders (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    world_id bigint NOT NULL REFERENCES public.worlds(id),
    island_id bigint NOT NULL REFERENCES public.islands(id),
    name character varying(64) NOT NULL,
    wonder_kind character varying(24) NOT NULL,
    x integer NOT NULL DEFAULT 50,
    y integer NOT NULL DEFAULT 50,
    level integer NOT NULL DEFAULT 0,
    status character varying(16) NOT NULL DEFAULT 'DORMANT',
    controlling_player_id bigint,
    controlling_alliance_id bigint,
    garrison jsonb DEFAULT '{}'::jsonb NOT NULL,
    invested_wood bigint DEFAULT 0 NOT NULL,
    invested_stone bigint DEFAULT 0 NOT NULL,
    invested_wheat bigint DEFAULT 0 NOT NULL,
    claimed_at timestamp with time zone,
    contested_until timestamp with time zone,
    last_tick_at timestamp with time zone DEFAULT now() NOT NULL
);

-- wonder captures reuse the movement system (OUT = assault, OCCUPY = claim/reinforce)
ALTER TABLE public.movements ADD COLUMN target_wonder_id bigint;



-- ============================================================================
-- COLOSSI — daily roaming PvE world bosses; damage accrues per alliance, rewards
-- split by damage share into alliance treasuries. (Appended feature migration.)
-- ============================================================================
ALTER TABLE public.movements ADD COLUMN target_colossus_id bigint;

CREATE TABLE public.colossi (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    world_id bigint NOT NULL,
    name character varying(48) NOT NULL,
    tier integer DEFAULT 1 NOT NULL,
    max_health bigint NOT NULL,
    current_health bigint NOT NULL,
    status character varying(16) NOT NULL,
    center_x integer NOT NULL,
    center_y integer NOT NULL,
    radius integer NOT NULL,
    start_angle double precision NOT NULL,
    arc_span double precision NOT NULL,
    attack_element character varying(8) NOT NULL,
    defense_fire integer NOT NULL,
    defense_wind integer NOT NULL,
    defense_earth integer NOT NULL,
    defense_water integer NOT NULL,
    spawned_at timestamp with time zone NOT NULL,
    despawn_at timestamp with time zone NOT NULL
);
CREATE INDEX colossi_world_status_idx ON public.colossi (world_id, status);

CREATE TABLE public.colossus_damage (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    colossus_id bigint NOT NULL,
    alliance_id bigint NOT NULL,
    accumulated_damage bigint DEFAULT 0 NOT NULL,
    last_contribution_at timestamp with time zone,
    CONSTRAINT colossus_damage_uq UNIQUE (colossus_id, alliance_id)
);
CREATE INDEX colossus_damage_colossus_idx ON public.colossus_damage (colossus_id);

-- ============================================================================
-- ESPIONAGE — Watchtower-based scouting: spy missions, intel reports, caught-spy alerts.
-- ============================================================================
CREATE TABLE public.spy_missions (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    spying_player_id bigint NOT NULL,
    origin_city_id bigint NOT NULL,
    target_city_id bigint NOT NULL,
    status character varying(16) NOT NULL,
    outcome character varying(16),
    started_at timestamp with time zone NOT NULL,
    resolves_at timestamp with time zone NOT NULL
);
CREATE INDEX spy_missions_due_idx ON public.spy_missions (status, resolves_at);

CREATE TABLE public.spy_reports (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_player_id bigint NOT NULL,
    target_city_id bigint NOT NULL,
    target_city_name character varying(64),
    outcome character varying(16) NOT NULL,
    revealed_troops jsonb,
    revealed_resources jsonb,
    revealed_buildings jsonb,
    captured_at timestamp with time zone NOT NULL,
    is_read boolean DEFAULT false NOT NULL
);
CREATE INDEX spy_reports_owner_idx ON public.spy_reports (owner_player_id);

CREATE TABLE public.spy_alerts (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_player_id bigint NOT NULL,
    spying_player_id bigint,
    spying_player_name character varying(64),
    target_city_id bigint,
    target_city_name character varying(64),
    caught_at timestamp with time zone NOT NULL,
    is_read boolean DEFAULT false NOT NULL
);
CREATE INDEX spy_alerts_owner_idx ON public.spy_alerts (owner_player_id);
