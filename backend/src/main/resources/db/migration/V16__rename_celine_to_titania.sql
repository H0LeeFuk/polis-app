-- Rename the fairy hero Celine -> Titania across persisted data.
update heroes   set hero_key='TITANIA' where hero_key='CELINE';
update heroes   set name='Titania'     where name='Celine';
update missions set unlocks_hero_key='TITANIA' where unlocks_hero_key='CELINE';
update missions set description=replace(description,'Celine','Titania') where description like '%Celine%';
