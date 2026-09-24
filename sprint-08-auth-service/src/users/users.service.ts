import { Injectable, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource } from 'typeorm';
import { User } from './user.entity';

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User)
    private usersRepository: Repository<User>,
    private dataSource: DataSource,
  ) {}

  async findOneByUsername(username: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ username });
  }

  async findOneById(id: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ id });
  }

  async validateAccountExists(accountId: number): Promise<boolean> {
    // Query the trade database schema to check if account exists
    const query = `SELECT 1 FROM public.accounts WHERE id = $1 LIMIT 1`;
    const result = await this.dataSource.query(query, [accountId]);
    return result.length > 0;
  }

  async create(
    username: string,
    passwordHash: string,
    roles: string[] = ['CUSTOMER'],
  ): Promise<User> {
    const user = this.usersRepository.create({
      username,
      passwordHash,
      roles,
    });
    return this.usersRepository.save(user);
  }

  async getAccountIdForUser(userId: string): Promise<number | null> {
    // Query the trade database to get the account linked to this user
    const query = `SELECT id FROM public.accounts WHERE user_id = $1 LIMIT 1`;
    const result = await this.dataSource.query(query, [userId]);
    return result.length > 0 ? result[0].id : null;
  }
}
