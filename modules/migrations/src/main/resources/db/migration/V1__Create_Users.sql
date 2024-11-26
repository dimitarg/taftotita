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

create type password_hash_algo as enum ('bcrypt');

create table user_passwords (
  user_id bigserial primary key references users(id) on delete cascade,
  algo password_hash_algo not null,
  hash text not null
);

create type email_status as enum ('scheduled', 'claimed', 'sent', 'error');

create table email_messages(
  id bigserial primary key,
  subject text,
  to_ text[] not null,
  cc text[] not null,
  bcc text[] not null,
  body text,
  status email_status,
  num_attempts int,
  error text,
  created_at timestamptz not null,
  last_attempted_at timestamptz
);
