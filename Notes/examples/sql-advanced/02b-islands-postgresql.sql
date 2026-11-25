SELECT '--- W10';
SELECT MIN(day) AS first_day, MAX(day) AS last_day, COUNT(*) AS days_in_row FROM (
  SELECT day, day - CAST(ROW_NUMBER() OVER (ORDER BY day) AS INT) AS grp FROM visits
) t GROUP BY grp ORDER BY first_day;
