-- (H2) V8 inserted the JSON literals without FORMAT JSON, so H2 stored them as JSON *strings*,
-- which fail to deserialize into Map<String,Integer>. Rewrite each row with FORMAT JSON.
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":10}' format json,                                         reward_payload = '{"wood":200,"stone":100}' format json                          where level = 1;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":15,"ARCHER":5}' format json,                              reward_payload = '{"wood":300,"silver":200}' format json                         where level = 2;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":20,"ARCHER":10}' format json,                             reward_payload = '{"stone":500,"silver":100}' format json                        where level = 3;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":25,"ARCHER":15,"HORSEMAN":5}' format json,                reward_payload = '{"ARCHER":1}' format json                                      where level = 4;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":30,"ARCHER":20,"HORSEMAN":10}' format json,               reward_payload = '{"wood":800,"silver":400}' format json                         where level = 5;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":20,"ARCHER":10,"HORSEMAN":5,"CATAPULT":5}' format json,   reward_payload = '{"HORSEMAN":2}' format json                                    where level = 6;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":25,"ARCHER":15,"CATAPULT":10}' format json,               reward_payload = '{"wood":1000,"stone":800,"silver":300}' format json            where level = 7;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":30,"ARCHER":15,"HORSEMAN":10,"CATAPULT":15}' format json, reward_payload = '{"CATAPULT":3}' format json                                    where level = 8;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":40,"ARCHER":20,"HORSEMAN":20,"CATAPULT":20}' format json, reward_payload = '{"silver":2000,"wood":1000}' format json                       where level = 9;
update bandit_camp_levels set defender_troops = '{"SWORDSMAN":40,"ARCHER":25,"HORSEMAN":20,"CATAPULT":15}' format json, reward_payload = '{"wood":1500,"stone":1500,"silver":1500,"HORSEMAN":5}' format json where level = 10;
