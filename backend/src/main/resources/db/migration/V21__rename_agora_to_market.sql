-- The AGORA building is now the MARKET (drives the trade marketplace + convoy logistics).
update city_buildings set type = 'MARKET' where type = 'AGORA';
