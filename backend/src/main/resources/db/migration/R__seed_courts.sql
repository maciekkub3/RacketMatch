-- Repeatable migration: Warsaw courts seed data
-- Re-runs only when checksum changes
DELETE FROM open_sessions;
DELETE FROM courts;

INSERT INTO courts (name, city, address, lat, lng, sports, playtomic_url) VALUES
('Warsaw Padel Club',        'Warszawa', 'ul. Wołoska 18, Mokotów',          52.1862, 21.0013, 'PADEL',         'https://playtomic.com/clubs/warsaw-padel-club'),
('Legia Tennis Club',        'Warszawa', 'ul. Łazienkowska 3, Śródmieście',  52.2211, 21.0353, 'TENNIS',        'https://playtomic.com/clubs/legia-tennis-club'),
('Kort Bema',                'Warszawa', 'ul. Bema 71, Wola',                52.2343, 20.9738, 'TENNIS,PADEL',  null),
('Korty Moczydło',           'Warszawa', 'ul. Górczewska 8, Wola',           52.2317, 20.9829, 'TENNIS',        null),
('Kortowo Ursynów',          'Warszawa', 'ul. Wąwozowa 14, Ursynów',         52.1478, 21.0614, 'TENNIS,PADEL',  'https://playtomic.com/clubs/kortowo-ursynow'),
('AZS UW Korty',             'Warszawa', 'ul. Banacha 2, Ochota',            52.2108, 20.9828, 'TENNIS',        null),
('Padel Arena Wilanów',      'Warszawa', 'ul. Klimczaka 1, Wilanów',         52.1641, 21.0869, 'PADEL',         'https://playtomic.com/clubs/padel-arena-wilanow'),
('Orlik Bemowo',             'Warszawa', 'ul. Powstańców Śl. 26, Bemowo',    52.2469, 20.9267, 'TENNIS',        null),
('Korty Saska Kępa',         'Warszawa', 'ul. Walecznych 10, Praga-Południe', 52.2336, 21.0669, 'TENNIS',        null),
('Padelmania Targówek',      'Warszawa', 'ul. Głębocka 66, Targówek',        52.2814, 21.0614, 'PADEL',         'https://playtomic.com/clubs/padelmania-targowek'),
('Korty Agrykola',           'Warszawa', 'ul. Myśliwiecka 9, Śródmieście',   52.2259, 21.0291, 'TENNIS',        null),
('SmashPoint Padel',         'Warszawa', 'ul. Obrzeżna 3, Mokotów',          52.1923, 21.0228, 'PADEL',         'https://playtomic.com/clubs/smashpoint-padel'),
('Korty Wola Park',          'Warszawa', 'ul. Górczewska 124, Wola',         52.2412, 20.9463, 'TENNIS,PADEL',  null),
('Padel Białołęka',          'Warszawa', 'ul. Mehoffera 80, Białołęka',      52.3128, 21.0296, 'PADEL',         'https://playtomic.com/clubs/padel-bialoleka'),
('Korty Żoliborz',           'Warszawa', 'ul. Powązkowska 44, Żoliborz',     52.2631, 20.9764, 'TENNIS',        null),

-- Poznań
('Poznań Tennis Club',       'Poznań',  'ul. Kórnicka 20, Grunwald',         52.3917, 16.9498, 'TENNIS',        null),
('Padel Poznań Strzeszyn',   'Poznań',  'ul. Strzeszyńska 45, Strzeszyn',    52.4452, 16.8821, 'PADEL',         'https://playtomic.com/clubs/padel-poznan-strzeszyn'),
('Korty AZS Poznań',         'Poznań',  'ul. Pułaskiego 4, Wilda',           52.3947, 16.9412, 'TENNIS',        null),
('Sokół Padel',              'Poznań',  'ul. Północna 3, Ogrody',            52.4211, 16.9004, 'PADEL',         'https://playtomic.com/clubs/sokol-padel-poznan'),
('Korty MOSiR Rataje',       'Poznań',  'ul. Łęczycka 23, Rataje',           52.3934, 17.0142, 'TENNIS',        null),
('Padel Arena Poznań',       'Poznań',  'ul. Bułgarska 17, Łazarz',          52.3841, 16.9003, 'PADEL',         'https://playtomic.com/clubs/padel-arena-poznan'),
('Korty Wilda',              'Poznań',  'ul. Rolna 101, Wilda',              52.3857, 16.9437, 'TENNIS,PADEL',  null),
('SmashZone Poznań',         'Poznań',  'ul. Serbska 6, Nowe Miasto',        52.4183, 16.9651, 'PADEL',         'https://playtomic.com/clubs/smashzone-poznan'),

-- Wrocław
('Wrocław Padel Club',       'Wrocław', 'ul. Hallera 52, Krzyki',            51.0868, 17.0412, 'PADEL',         'https://playtomic.com/clubs/wroclaw-padel-club'),
('Korty WKT Wrocław',        'Wrocław', 'ul. Świdnicka 47, Śródmieście',     51.1012, 17.0312, 'TENNIS',        null),
('Padel Wrocław Fabryczna',  'Wrocław', 'ul. Strzegomska 140, Fabryczna',    51.1189, 16.9831, 'PADEL',         'https://playtomic.com/clubs/padel-wroclaw-fabryczna'),
('Korty Olimpia',            'Wrocław', 'ul. Wittiga 10, Popowice',          51.1231, 17.0014, 'TENNIS',        null),
('AZS AWF Wrocław',          'Wrocław', 'al. Ignacego Jana Paderewskiego 35', 51.1098, 17.0763, 'TENNIS',        null),
('Padel Hub Wrocław',        'Wrocław', 'ul. Braniborska 59, Nadodrze',      51.1237, 17.0189, 'PADEL',         'https://playtomic.com/clubs/padel-hub-wroclaw'),
('Korty Partynice',          'Wrocław', 'ul. Zwycięska 2, Krzyki',           51.0712, 17.0603, 'TENNIS,PADEL',  null),
('SmashCourt Wrocław',       'Wrocław', 'ul. Muchoborska 18, Fabryczna',     51.1298, 16.9712, 'PADEL',         'https://playtomic.com/clubs/smashcourt-wroclaw'),

-- Szczecin
('Korty MOSiR Szczecin',     'Szczecin', 'ul. Twardowskiego 10, Śródmieście', 53.4312, 14.5603, 'TENNIS',       null),
('Padel Szczecin',           'Szczecin', 'ul. Ku Słońcu 61, Warszewo',        53.4721, 14.5183, 'PADEL',        'https://playtomic.com/clubs/padel-szczecin'),
('Korty AZS Szczecin',       'Szczecin', 'ul. Piastów 18, Centrum',           53.4198, 14.5413, 'TENNIS',       null),
('Padel Arena Szczecin',     'Szczecin', 'ul. Struga 3, Centrum',             53.4261, 14.5514, 'PADEL',        'https://playtomic.com/clubs/padel-arena-szczecin'),
('Korty Dąbie',              'Szczecin', 'ul. Przestrzenna 14, Dąbie',        53.3987, 14.6123, 'TENNIS,PADEL', null),
('SmashPoint Szczecin',      'Szczecin', 'ul. Gdańska 24, Niebuszewo',        53.4412, 14.5678, 'PADEL',        'https://playtomic.com/clubs/smashpoint-szczecin');
