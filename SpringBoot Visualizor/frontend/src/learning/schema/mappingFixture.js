/** Test fixtures only. Nothing in the application imports this file. */
// A FIXTURE with the same shape as GET /api/lab/jpa/mapping. The real mapping is read from the server at run time; the
// backend tests (JpaLabInfrastructureIntegrationTest) check that the server's real values are what this fixture assumes.
const attr = (name, extra = {}) => ({ name, javaType: 'String', column: name, id: false, association: null, target: null, owning: false, mappedBy: null, fetch: null, cascade: [], orphanRemoval: false, joinColumn: null, joinTable: null, annotation: null, ...extra })
const col = (name, extra = {}) => ({ name, type: 'bigint', nullable: true, primaryKey: false, unique: false, ...extra })

export const mapping = {
  entities: [
    { id: 'Department', javaClass: 'x.Department', table: 'jpa_lab_department', attributes: [
      attr('id', { id: true, javaType: 'Long' }), attr('runKey'), attr('name'),
      attr('employees', { association: 'OneToMany', target: 'Employee', owning: false, mappedBy: 'department', fetch: 'LAZY', annotation: '@OneToMany(mappedBy = "department")' }),
    ] },
    { id: 'Employee', javaClass: 'x.Employee', table: 'jpa_lab_employee', attributes: [
      attr('id', { id: true }), attr('runKey'), attr('name'),
      attr('department', { association: 'ManyToOne', target: 'Department', owning: true, fetch: 'LAZY', joinColumn: 'department_id', annotation: '@ManyToOne(fetch = LAZY)' }),
    ] },
    { id: 'Student', javaClass: 'x.Student', table: 'jpa_lab_student', attributes: [
      attr('id', { id: true }),
      attr('courses', { association: 'ManyToMany', target: 'Course', owning: true, fetch: 'LAZY', joinTable: { name: 'jpa_lab_enrollment', joinColumn: 'student_id', inverseJoinColumn: 'course_id' }, annotation: '@ManyToMany' }),
    ] },
    { id: 'Course', javaClass: 'x.Course', table: 'jpa_lab_course', attributes: [
      attr('id', { id: true }),
      attr('students', { association: 'ManyToMany', target: 'Student', owning: false, mappedBy: 'courses', fetch: 'LAZY', annotation: '@ManyToMany(mappedBy = "courses")' }),
    ] },
  ],
  tables: [
    { id: 'jpa_lab_department', joinTable: false, columns: [col('id', { primaryKey: true }), col('run_key'), col('name')], foreignKeys: [] },
    { id: 'jpa_lab_employee', joinTable: false, columns: [col('id', { primaryKey: true }), col('run_key'), col('name'), col('department_id')], foreignKeys: [{ constraint: 'fk', column: 'department_id', referencedTable: 'jpa_lab_department', referencedColumn: 'id' }] },
    { id: 'jpa_lab_student', joinTable: false, columns: [col('id', { primaryKey: true })], foreignKeys: [] },
    { id: 'jpa_lab_course', joinTable: false, columns: [col('id', { primaryKey: true })], foreignKeys: [] },
    { id: 'jpa_lab_enrollment', joinTable: true, columns: [col('student_id', { primaryKey: true }), col('course_id', { primaryKey: true })], foreignKeys: [
      { constraint: 'a', column: 'student_id', referencedTable: 'jpa_lab_student', referencedColumn: 'id' },
      { constraint: 'b', column: 'course_id', referencedTable: 'jpa_lab_course', referencedColumn: 'id' }] },
  ],
  edges: [
    { id: 'Department->jpa_lab_department', source: 'Department', target: 'jpa_lab_department', kind: 'MAPS_TO', label: 'mapped to table' },
    { id: 'Employee->jpa_lab_employee', source: 'Employee', target: 'jpa_lab_employee', kind: 'MAPS_TO', label: 'mapped to table' },
    { id: 'Student->jpa_lab_student', source: 'Student', target: 'jpa_lab_student', kind: 'MAPS_TO', label: 'mapped to table' },
    { id: 'Course->jpa_lab_course', source: 'Course', target: 'jpa_lab_course', kind: 'MAPS_TO', label: 'mapped to table' },
    { id: 'Department->Employee', source: 'Department', target: 'Employee', kind: 'ASSOCIATION', label: '@OneToMany(mappedBy = "department")' },
    { id: 'Employee->Department', source: 'Employee', target: 'Department', kind: 'ASSOCIATION', label: '@ManyToOne(fetch = LAZY)' },
    { id: 'Student->Course', source: 'Student', target: 'Course', kind: 'ASSOCIATION', label: '@ManyToMany' },
    { id: 'Course->Student', source: 'Course', target: 'Student', kind: 'ASSOCIATION', label: '@ManyToMany(mappedBy = "courses")' },
    { id: 'jpa_lab_employee->jpa_lab_department', source: 'jpa_lab_employee', target: 'jpa_lab_department', kind: 'FOREIGN_KEY', label: 'department_id references jpa_lab_department.id' },
    { id: 'jpa_lab_enrollment->jpa_lab_student', source: 'jpa_lab_enrollment', target: 'jpa_lab_student', kind: 'FOREIGN_KEY', label: 'student_id references jpa_lab_student.id' },
    { id: 'jpa_lab_enrollment->jpa_lab_course', source: 'jpa_lab_enrollment', target: 'jpa_lab_course', kind: 'FOREIGN_KEY', label: 'course_id references jpa_lab_course.id' },
  ],
}

