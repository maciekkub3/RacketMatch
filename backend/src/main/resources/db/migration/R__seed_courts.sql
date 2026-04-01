-- Repeatable migration: Warsaw courts seed data
-- Re-runs only when checksum changes
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
('Korty Żoliborz',           'Warszawa', 'ul. Powązkowska 44, Żoliborz',     52.2631, 20.9764, 'TENNIS',        null);
