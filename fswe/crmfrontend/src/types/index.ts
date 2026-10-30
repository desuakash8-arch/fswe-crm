export interface User {
  userId: number;
  name: string;
  email: string;
  role: string;
}

export interface Customer {
  customerId: number;
  name: string;
  email: string;
  phone: string;
  company: string;
  address: string;
  status: string;
  assignedTo?: User;
  createdAt?: string;
}