create table users (
  id bigserial primary key,
  full_name text,
  email text not null unique
);

create table user_roles (
  user_id bigserial not null references users(id) on delete cascade,
  role varchar(100) not null,
  primary key (user_id, role)
);


create index user_roles_user_id_idx on user_roles(user_id);
create index user_roles_role_idx on user_roles(role);

create table user_passwords (
  user_id bigserial primary key references users(id) on delete cascade, 
  password_hash text not null
);
