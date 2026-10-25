import * as path from 'path';
import { Author, Post, ProfileContext } from './models';

declare const nunjucks: { configure(path: string | string[], options?: object): Env };
declare interface Env { addFilter(name: string, fn: Function): void; addGlobal(name: string, value: unknown): void }
declare const app: { set(key: string, value: string): void; get(route: string, handler: (req: unknown, res: Res) => void): void };
declare interface Res { render(view: string, context?: object): void }
declare function loadPosts(): Post[];

const env = nunjucks.configure(path.join(__dirname, '../views'), { autoescape: true });
app.set('view engine', 'njk');

env.addFilter('initials', (author: Author, upper: boolean = true): string[] =>
  author.name.split(' ').map(p => (upper ? p[0].toUpperCase() : p[0])));
env.addGlobal('site', { name: 'My Blog', url: 'https://example.com' });

app.get('/', (req, res) => {
  const posts = loadPosts();
  res.render('blog', { posts, featured: posts[0] });
});

app.get('/profile', (req, res) => {
  const profile: ProfileContext = { user: loadPosts()[0].author, visits: 3 };
  res.render('profile', profile);
});
