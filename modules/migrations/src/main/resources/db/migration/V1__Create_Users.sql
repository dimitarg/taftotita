create table users (
  id bigserial primary key,
  full_name text,
  email text not null unique,
  deleted boolean not null default false
);

create table user_roles (
  user_id bigserial not null references users(id) on delete restrict,
  role varchar(100) not null,
  deleted boolean not null default false,
  primary key (user_id, role)
);
