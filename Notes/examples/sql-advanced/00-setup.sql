DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS emp;
DROP TABLE IF EXISTS dept;

CREATE TABLE dept (
  id   INT PRIMARY KEY,
  name VARCHAR(30) NOT NULL
);

CREATE TABLE emp (
  id         INT PRIMARY KEY,
  name       VARCHAR(30) NOT NULL,
  dept_id    INT NULL,
  manager_id INT NULL,
  salary     INT NOT NULL,
  hired      DATE NOT NULL,
  FOREIGN KEY (dept_id) REFERENCES dept(id),
  FOREIGN KEY (manager_id) REFERENCES emp(id)
);

CREATE TABLE orders (
  id     INT PRIMARY KEY,
  emp_id INT NOT NULL,
  amount INT NOT NULL,
  placed DATE NOT NULL,
  FOREIGN KEY (emp_id) REFERENCES emp(id)
);

INSERT INTO dept VALUES (1, 'Engineering'), (2, 'Sales'), (3, 'HR'), (4, 'Legal');

INSERT INTO emp VALUES
 (1, 'Asha',   1, NULL, 150000, '2018-03-01'),
 (2, 'Ben',    1, 1,    120000, '2019-06-15'),
 (3, 'Chitra', 1, 1,    120000, '2020-01-10'),
 (4, 'Dev',    1, 2,     90000, '2021-09-01'),
 (5, 'Esha',   2, 1,    110000, '2019-02-20'),
 (6, 'Farid',  2, 5,     80000, '2022-04-05'),
 (7, 'Gita',   2, 5,     80000, '2023-07-17'),
 (8, 'Hari',   3, 1,     70000, '2020-11-30'),
 (9, 'Isha',   NULL, 1,  65000, '2024-02-12');

INSERT INTO orders VALUES
 (1, 5,  500, '2026-01-05'),
 (2, 5,  700, '2026-01-20'),
 (3, 6,  300, '2026-02-03'),
 (4, 6,  450, '2026-02-18'),
 (5, 7,  900, '2026-02-25'),
 (6, 5, 1200, '2026-03-02'),
 (7, 7,  250, '2026-03-15'),
 (8, 6,  800, '2026-03-28'),
 (9, 5,  150, '2026-04-01');

DROP TABLE IF EXISTS visits;
CREATE TABLE visits (day DATE PRIMARY KEY);
INSERT INTO visits VALUES ('2026-03-01'), ('2026-03-02'), ('2026-03-03'), ('2026-03-06'), ('2026-03-07'), ('2026-03-10');
