/*
 * ICC: creates our seven people and the "Applications" group on the ServiceNow instance (D37).
 * This file is ServiceNow-side code. Our application never runs it; it is kept here so it is reviewed and not lost.
 *
 * WHAT IT DOES (it only adds what is missing, so running it twice is safe):
 *   1. the group "Applications", if it does not exist (Service Desk, Network and Database already do);
 *   2. the seven users, found by email, created if missing (no password is set, nobody can log in with them);
 *   3. each engineer as a member of the team's group.
 * WHAT IT DOES NOT DO: delete or change an existing record, give a role, or set a password.
 *
 * HOW TO RUN (you, not Claude): read it first, then in the instance open
 *   System Definition > Scripts - Background, scope "global", paste this file, press "Run script".
 *   The output lists what already existed and what was created. Afterwards run, in the project:
 *   node scripts/check-servicenow.mjs      (the users and the group should now say PASS)
 */
(function () {
  var PEOPLE = [
    // email, first name, last name, group of the team (empty for people without a team)
    ['mohit@icc.com',   'Mohit',   'Bali',        ''],
    ['priya@icc.com',   'Priya',   'Nair',        'Service Desk'],
    ['sai@icc.com',     'Sai',     'Vivek',       'Network'],
    ['sachin@icc.com',  'Sachin',  'Suryavanshi', 'Database'],
    ['vinod@icc.com',   'Vinod',   '',            'Applications'],
    ['ananya@icc.com',  'Ananya',  'Iyer',        ''],
    ['rohan@icc.com',   'Rohan',   'Mehta',       '']
  ];

  function say(text) { gs.print(text); }

  function ensureGroup(name) {
    var g = new GlideRecord('sys_user_group');
    g.addQuery('name', name);
    g.query();
    if (g.next()) {
      say('group exists:   ' + name);
      return g.getUniqueValue();
    }
    g.initialize();
    g.name = name;
    g.description = 'Created for the Incident Command Center sync';
    g.active = true;
    var id = g.insert();
    say('group CREATED:  ' + name);
    return id;
  }

  function ensureUser(email, first, last) {
    var u = new GlideRecord('sys_user');
    u.addQuery('email', email);
    u.query();
    if (u.next()) {
      say('user exists:    ' + email);
      return u.getUniqueValue();
    }
    u.initialize();
    u.user_name = email;
    u.email = email;
    u.first_name = first;
    u.last_name = last;
    u.active = true;
    var id = u.insert();
    say('user CREATED:   ' + email);
    return id;
  }

  function ensureMember(userId, groupId, label) {
    var m = new GlideRecord('sys_user_grmember');
    m.addQuery('user', userId);
    m.addQuery('group', groupId);
    m.query();
    if (m.next()) {
      say('member exists:  ' + label);
      return;
    }
    m.initialize();
    m.user = userId;
    m.group = groupId;
    m.insert();
    say('member CREATED: ' + label);
  }

  ensureGroup('Applications');
  for (var i = 0; i < PEOPLE.length; i++) {
    var p = PEOPLE[i];
    var userId = ensureUser(p[0], p[1], p[2]);
    if (p[3]) {
      ensureMember(userId, ensureGroup(p[3]), p[0] + ' in ' + p[3]);
    }
  }
  say('done');
})();
