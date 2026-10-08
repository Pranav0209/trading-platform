import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';

@Entity({ name: 'users', schema: 'auth' })
export class User {
  @PrimaryGeneratedColumn('increment')
  id: number;

  @Column({ type: 'varchar', length: 50, unique: true })
  username: string;

  @Column({ type: 'varchar', length: 255, unique: true })
  email: string;

  @Column({ name: 'password_hash', type: 'varchar', length: 255 })
  passwordHash: string;

  @Column({ type: 'varchar', length: 64 })
  role: string;

  @Column({ type: 'varchar', length: 32, default: 'PENDING' })
  status: string;

  @Column({ name: 'notification_channel', type: 'varchar', length: 20, default: 'EMAIL' })
  notificationChannel: string;

  @Column({ name: 'otp_code', type: 'varchar', length: 10, nullable: true })
  otpCode?: string | null;

  @Column({ name: 'otp_expires_at', type: 'timestamp with time zone', nullable: true })
  otpExpiresAt?: Date | null;

  @Column({ name: 'pending_new_email', type: 'varchar', length: 255, nullable: true })
  pendingNewEmail?: string | null;

  @Column({ name: 'created_at', type: 'timestamp with time zone', default: () => 'CURRENT_TIMESTAMP' })
  createdAt: Date;

  @Column({ name: 'updated_at', type: 'timestamp with time zone', default: () => 'CURRENT_TIMESTAMP' })
  updatedAt: Date;
}
