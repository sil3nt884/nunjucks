export interface Author {
  name: string;
  email: string;
}

export interface Post {
  title: string;
  slug: string;
  author: Author;
  tags: string[];
}

export interface ProfileContext {
  user: Author;
  visits: number;
}
